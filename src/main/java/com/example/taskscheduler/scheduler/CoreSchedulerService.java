package com.example.taskscheduler.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Import ExecutionMode
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired; // Added
import org.springframework.context.ApplicationContext; // Added
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler; // Added
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.taskscheduler.dao.TaskCalendarDao;
import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.enums.ExecutionMode;
import com.example.taskscheduler.enums.ExecutionPattern;
import com.example.taskscheduler.service.BeanTaskExecutor;
import com.example.taskscheduler.service.DistributedLockService;
import com.example.taskscheduler.service.NotificationService;
import com.example.taskscheduler.service.WorkflowExecutionService;

/**
 * Core service responsible for managing the lifecycle of scheduled tasks. It
 * implements {@link SchedulingConfigurer} to dynamically register and manage
 * tasks based on configurations stored in the database. It handles scheduling,
 * cancellation, manual triggering, and integrates with other services for lock
 * management, task execution, and notifications.
 */
@Service
public class CoreSchedulerService implements SchedulingConfigurer, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(CoreSchedulerService.class);
    // private final String instanceId = UUID.randomUUID().toString(); // Instance
    // ID now comes from DistributedLockService

    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    // Ensure this is the ThreadPoolTaskScheduler for scheduling with delay/specific
    // time
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private ApplicationContext applicationContext; // To get BeanTaskExecutor
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private TaskCalendarDao taskCalendarDao;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    @org.springframework.context.annotation.Lazy // To handle potential circular dependency
    private WorkflowExecutionService workflowExecutionService;

    private BeanTaskExecutor beanTaskExecutor; // Lazily initialized

    // Keep track of scheduled tasks
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Integer, CronTask> cronTasks = new ConcurrentHashMap<>(); // For potential re-registration needs
    private ScheduledTaskRegistrar taskRegistrar;

    /**
     * Initializes the service after dependency injection. It loads and
     * schedules all active tasks from the database.
     */
    public void init() {
        // beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class); //
        // Initialize if needed immediately, or on first use
        loadAndScheduleInitialTasks();
    }

    /**
     * Configures tasks with the Spring task registrar. This method is part of
     * the {@link SchedulingConfigurer} interface.
     *
     * @param taskRegistrar The registrar for scheduled tasks.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        // If taskScheduler is not autowired or needs specific configuration not
        // achievable via autowiring alone:
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(15); // Example pool size
        threadPoolTaskScheduler.setThreadNamePrefix("core-scheduler-");
        threadPoolTaskScheduler.initialize();
        this.taskScheduler = threadPoolTaskScheduler;
        taskRegistrar.setScheduler(this.taskScheduler);
    }

    /**
     * Loads all active tasks from the database and schedules them. This is
     * typically called on application startup.
     */
    public void loadAndScheduleInitialTasks() {
        logger.info("Loading and scheduling initial tasks...");
        List<TaskConfig> activeTasks = taskConfigDao.findAllActiveTasks();
        activeTasks.forEach(this::scheduleTask); // `this::scheduleTask` implicitly uses the class's taskScheduler
        logger.info("Scheduled {} initial tasks from a list of {} active tasks found.", scheduledTasks.size(),
                activeTasks.size());
    }

    /**
     * Schedules a single task based on its configuration. If the task is
     * already scheduled, it will not be scheduled again unless cancelled first.
     *
     * @param taskConfig The configuration of the task to schedule.
     * @return {@code true} if the task was successfully scheduled,
     *         {@code false} otherwise (e.g., inactive, invalid cron).
     */
    public boolean scheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null || !taskConfig.isActive()) {
            logger.warn("Task config is null or inactive, cannot schedule: Task ID {}",
                    taskConfig != null ? taskConfig.getTaskId() : "null");
            return false;
        }
        // Prevent duplicate scheduling if already present and not cancelled.
        synchronized (scheduledTasks) {
            if (scheduledTasks.containsKey(taskConfig.getTaskId())) {
                logger.warn(
                        "Task {} ({}) is already scheduled. To reschedule, cancel it first or use rescheduleTask().",
                        taskConfig.getTaskId(), taskConfig.getTaskName());
                return false; // Or handle as an update if cron changed, but rescheduleTask is better for that
            }
        }

        Runnable taskRunnable = createTaskRunnable(taskConfig);
        if (taskRunnable == null) { // Should not happen if taskConfig is valid
            logger.error("Could not create runnable for task: {} (ID: {})", taskConfig.getTaskName(),
                    taskConfig.getTaskId());
            return false;
        }

        try {
            // Use CustomTaskTrigger instead of standard CronTrigger
            CustomTaskTrigger customTaskTrigger = new CustomTaskTrigger(taskConfig, taskCalendarDao);

            ScheduledFuture<?> future;

            future = taskScheduler.schedule(taskRunnable, customTaskTrigger);

            synchronized (scheduledTasks) {
                scheduledTasks.put(taskConfig.getTaskId(), future);
            }

            logger.info("Task {} ({}) scheduled successfully with CustomTaskTrigger using cron: [{}].",
                    taskConfig.getTaskId(), taskConfig.getTaskName(), taskConfig.getCronExpression());
            return true;
        } catch (IllegalArgumentException e) { // This might now be caught earlier by CronTrigger constructor if cron is
                                               // invalid
            logger.error("Invalid configuration for task {} ({}): Cron='{}', Error: {}",
                    taskConfig.getTaskId(), taskConfig.getTaskName(), taskConfig.getCronExpression(), e.getMessage());
            return false;
        } catch (Exception e) { // Catch other potential exceptions during scheduling
            logger.error("Failed to schedule task {} ({}): {}",
                    taskConfig.getTaskId(), taskConfig.getTaskName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Creates a {@link Runnable} for the given task configuration. This
     * runnable encapsulates the logic for executing the task. Key aspects
     * include:
     * <ul>
     * <li>Logging task attempts and outcomes to {@link TaskExecuteLog}.</li>
     * <li>Handling {@link ExecutionMode#CLUSTER}: Acquires a distributed lock
     * (derived from task ID) to ensure single-instance execution in a cluster.
     * If the lock is not acquired, the task is skipped.
     * {@link ExecutionMode#BROADCAST} tasks run without locking.</li>
     * <li>Performing pre-execution checks: start/end dates, calendar
     * exclusions, and daily time exclusions. If any exclusion applies, the task
     * is skipped.</li>
     * <li>Executing the task based on its {@code taskType} (Bean, HTTP, Shell,
     * Workflow) via respective executors.</li>
     * <li>Capturing exceptions, including
     * {@link BeanTaskExecutor.TaskTimeoutException}.</li>
     * <li>Updating the final status in {@link TaskExecuteLog}.</li>
     * <li>Releasing the distributed lock (if acquired) in a finally block.</li>
     * <li>Triggering notifications via {@link NotificationService} based on
     * task outcome and configuration.</li>
     * </ul>
     *
     * @param taskConfig The configuration of the task.
     * @return A {@link Runnable} that can be scheduled.
     */
    private Runnable createTaskRunnable(TaskConfig taskConfig) {
        // For cron-scheduled tasks, the first attempt is always 1.
        // The CustomTaskTrigger will use this Runnable.
        // Cron tasks are considered "NORMAL" and have no parent.
        return new TaskExecutionJob(
                taskConfig,
                this.applicationContext,
                this.taskExecuteLogDao,
                this.distributedLockService,
                this, // Pass self for callback to handleTaskCompletion
                this.notificationService,
                this.distributedLockService.getSchedulerInstanceId(),
                1, // Initial attempt for a cron-scheduled run
                ExecutionPattern.NORMAL.name(),
                // initialTaskPattern for cron
                null, // parentLogId for cron
                null, // effectiveBeanParametersJson for cron
                null // workflowNodeId for cron
        );
    }

    /**
     * Handles the completion of a task execution attempt, scheduling retries if
     * applicable.
     *
     * @param taskConfig             The configuration of the task that completed.
     * @param finalStatus            The final status of the just-completed attempt
     *                               (e.g.,
     *                               "FAILED", "TIMED_OUT", "SUCCESS").
     * @param completedAttemptNumber The attempt number that just completed.
     * @param executionLogId         The ID of the log entry for the completed
     *                               attempt.
     * @param originalInitialPattern The initialTaskPattern of the first attempt
     *                               in this sequence.
     * @param originalInitialPattern The initialTaskPattern of the first attempt
     *                               in this sequence.
     * @param originalParentLogId    The parentLogId of the first attempt in this
     *                               sequence (if any).
     * @param workflowNodeId         The ID of the workflow node, if this task is
     *                               part
     *                               of a workflow.
     */
    public void handleTaskCompletion(TaskConfig taskConfig, String finalStatus, int completedAttemptNumber,
            Long executionLogId,
            String originalInitialPattern, Long originalParentLogId, String workflowNodeId) {
        MDC.put("task_id", String.valueOf(taskConfig.getTaskId()));
        MDC.put("task_name", taskConfig.getTaskName());
        MDC.put("execute_no", String.valueOf(executionLogId)); // Log ID of the completed/failed attempt
        MDC.put("attempt_no", String.valueOf(completedAttemptNumber));

        if ("FAILED".equals(finalStatus) || "TIMED_OUT".equals(finalStatus)) {
            Integer maxRetries = taskConfig.getMaxRetryAttempts();
            if (maxRetries == null) {
                maxRetries = 0;
            }

            if (completedAttemptNumber <= maxRetries) { // If current attempt is less than or equal to allowed retries
                int nextAttempt = completedAttemptNumber + 1;

                // If this was the last allowed attempt (completedAttemptNumber == maxRetries),
                // and it failed, we log it and do not schedule another.
                // The retry should happen if completedAttemptNumber < maxRetries.
                // Example: maxRetries = 0. Attempt 1 fails. 1 < 0 is false. No retry.
                // Example: maxRetries = 1. Attempt 1 fails. 1 < 1 is false. No retry.
                // This means maxRetries is "number of *additional* attempts".
                // So, if maxRetries = 1, total attempts = 1 (original) + 1 (retry) = 2.
                // We schedule a retry (attempt #2) if original attempt #1 fails.
                // The `nextAttempt` should not exceed `maxRetries + 1`.
                // So, if `completedAttemptNumber` (which just failed) is less than `maxRetries
                // + 1` (total allowed runs), schedule next.
                // And `nextAttempt` (which is `completedAttemptNumber + 1`) is the one being
                // scheduled.
                if (completedAttemptNumber >= (maxRetries + 1)) { // This means all attempts (original + retries) are
                                                                  // done
                    logger.info("Task ID {} failed on attempt {} and max retries ({}) reached. No more retries.",
                            taskConfig.getTaskId(), completedAttemptNumber, maxRetries);
                    taskExecuteLogDao.updateLogRtnMsg(executionLogId,
                            "Failed attempt " + completedAttemptNumber + ", max retries (" + maxRetries + ") reached.");
                    MDC.clear();
                    return;
                }

                Integer intervalSeconds = taskConfig.getRetryIntervalSeconds();
                if (intervalSeconds == null || intervalSeconds < 1) {
                    intervalSeconds = 30; // Default
                }
                logger.info(
                        "Task ID {} failed on attempt {}. Scheduling retry attempt {} in {} seconds. (Max total attempts: {})",
                        taskConfig.getTaskId(), completedAttemptNumber, nextAttempt, intervalSeconds, maxRetries + 1);

                taskExecuteLogDao.updateLogRtnMsg(executionLogId,
                        "Failed attempt " + completedAttemptNumber + ", scheduling retry " + nextAttempt + ".");

                TaskExecutionJob retryJob = new TaskExecutionJob(
                        taskConfig,
                        this.applicationContext,
                        this.taskExecuteLogDao,
                        this.distributedLockService,
                        this,
                        this.notificationService,
                        this.distributedLockService.getSchedulerInstanceId(),
                        nextAttempt,
                        originalInitialPattern,
                        originalParentLogId,
                        null, // effectiveBeanParametersJson for retries (use original task config)
                        workflowNodeId // Pass along the workflowNodeId for retries
                );

                // Calculate retry interval with exponential backoff
                Float multiplier = taskConfig.getRetryIntervalMultiplier();
                if (multiplier == null || multiplier <= 0) {
                    multiplier = 1.0f; // Default multiplier
                }

                // For exponential backoff: interval * (multiplier ^ (attemptNumber - 1))
                // For attempt 1: interval * 1
                // For attempt 2: interval * multiplier
                // For attempt 3: interval * multiplier^2
                // etc.
                double delaySeconds = intervalSeconds * Math.pow(multiplier, nextAttempt - 1);
                long finalDelaySeconds = Math.round(delaySeconds);

                Instant nextExecutionTime = Instant.now().plusSeconds(finalDelaySeconds);
                try {
                    this.taskScheduler.schedule(retryJob, nextExecutionTime); // Use the class field taskScheduler
                    logger.info("Task ID {} retry attempt {} scheduled for {}.", taskConfig.getTaskId(), nextAttempt,
                            nextExecutionTime);
                } catch (Exception e) {
                    logger.error("Error scheduling retry for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(),
                            e);
                    taskExecuteLogDao.updateLogRtnMsg(executionLogId, "Failed attempt " + completedAttemptNumber
                            + ". Retry attempt " + nextAttempt + " could not be scheduled: " + e.getMessage());
                }
            } else {
                logger.info("Task ID {} failed on final attempt {} (max configured retries: {}). No more retries.",
                        taskConfig.getTaskId(), completedAttemptNumber, maxRetries);
                taskExecuteLogDao.updateLogRtnMsg(executionLogId, "Failed on final attempt " + completedAttemptNumber
                        + ". Max retries (" + maxRetries + ") exhausted.");
            }
        } else {
            logger.info("Task ID {} completed with status {} on attempt {}.", taskConfig.getTaskId(), finalStatus,
                    completedAttemptNumber);
            if (completedAttemptNumber > 1 && "SUCCESS".equals(finalStatus)) {
                taskExecuteLogDao.updateLogRtnMsg(executionLogId,
                        "Successfully completed on attempt " + completedAttemptNumber + ".");
            }
        }

        // Notify WorkflowExecutionService if this was a workflow node and it's terminal
        if (originalParentLogId != null && workflowNodeId != null) {
            boolean isMaxRetriesReached = taskConfig.getMaxRetryAttempts() == null ? false
                    : completedAttemptNumber > taskConfig.getMaxRetryAttempts();
            // Simpler: if maxRetryAttempts is 0, completedAttemptNumber 1 is already > 0.
            // Max total attempts = maxRetryAttempts + 1. If completedAttemptNumber >=
            // maxRetryAttempts + 1, all attempts are done.
            if (taskConfig.getMaxRetryAttempts() != null && completedAttemptNumber > taskConfig.getMaxRetryAttempts()) { // simplified
                                                                                                                         // this
                                                                                                                         // from
                                                                                                                         // before
                isMaxRetriesReached = true;
            } else if (taskConfig.getMaxRetryAttempts() == null && completedAttemptNumber > 0) { // No retries
                                                                                                 // configured, first
                                                                                                 // attempt is final if
                                                                                                 // failed
                isMaxRetriesReached = true;
            }

            boolean isTerminal = "SUCCESS".equals(finalStatus)
                    || (("FAILED".equals(finalStatus) || "TIMED_OUT".equals(finalStatus)) && isMaxRetriesReached);

            if (isTerminal) {
                if (this.workflowExecutionService != null) {
                    logger.debug(
                            "Notifying WES of terminal node. ParentLogId: {}, NodeId: {}, Status: {}, LastLogId: {}",
                            originalParentLogId, workflowNodeId, finalStatus, executionLogId);
                    this.workflowExecutionService.processNodeCompletion(originalParentLogId, workflowNodeId,
                            finalStatus, executionLogId);
                } else {
                    logger.warn(
                            "WorkflowExecutionService not available in CoreSchedulerService to notify node completion for workflowLogId: {}, nodeId: {}",
                            originalParentLogId, workflowNodeId);
                }
            }
        }
        MDC.clear();
    }

    // --- Helper methods for exclusion checks (checkDateExclusions, etc.) remain
    // unchanged ---
    /**
     * Checks if the task should be excluded based on its configured start and
     * end dates.
     *
     * @param taskConfig The task configuration.
     * @return A reason string if excluded, {@code null} otherwise.
     */
    private String checkDateExclusions(TaskConfig taskConfig) {
        java.util.Date now = new java.util.Date();
        if (taskConfig.getStartDate() != null && taskConfig.getStartDate().after(now)) {
            return "Skipped: Start date " + taskConfig.getStartDate() + " is in the future.";
        }
        if (taskConfig.getEndDate() != null && taskConfig.getEndDate().before(now)) {
            return "Skipped: End date " + taskConfig.getEndDate() + " is in the past.";
        }
        return null;
    }

    /**
     * Checks if the task should be excluded based on a configured task
     * calendar. If a {@code taskCalendarGroup} is specified, it checks if the
     * current date is marked as a non-working day in that calendar.
     *
     * @param taskConfig The task configuration.
     * @return A reason string if excluded, {@code null} otherwise.
     */
    private String checkCalendarExclusions(TaskConfig taskConfig) {
        if (!StringUtils.hasText(taskConfig.getTaskCalendarGroup())) {
            return null;
        }
        return taskCalendarDao.findCalendarByName(taskConfig.getTaskCalendarGroup())
                .flatMap(calendar -> {
                    java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                    return taskCalendarDao.findCalendarDayByCalendarIdAndDate(calendar.getCalendarId(), today)
                            .filter(calendarDay -> !calendarDay.isWorkingDay())
                            .map(nonWorkingDay -> "Skipped: Current date " + today + " is a non-working day ("
                                    + nonWorkingDay.getDescription() + ") in calendar group '"
                                    + taskConfig.getTaskCalendarGroup() + "'.");
                })
                .orElse(null);
    }

    /**
     * Checks if the task should be excluded based on configured time ranges for
     * the current day. Time ranges are specified in {@code taskExcludeTimes}
     * like "HH:mm-HH:mm,HH:mm-HH:mm".
     *
     * @param taskConfig The task configuration.
     * @return A reason string if excluded, {@code null} otherwise.
     */
    private String checkTimeExclusions(TaskConfig taskConfig) {
        if (!StringUtils.hasText(taskConfig.getTaskExcludeTimes())) {
            return null;
        }
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        String[] ranges = taskConfig.getTaskExcludeTimes().split(",");
        for (String range : ranges) {
            String[] times = range.trim().split("-");
            if (times.length == 2) {
                try {
                    java.time.LocalTime startTime = java.time.LocalTime.parse(times[0].trim());
                    java.time.LocalTime endTime = java.time.LocalTime.parse(times[1].trim());
                    // Assuming ranges are within the same day (e.g., 00:00-08:00).
                    // More complex logic would be needed for overnight ranges (e.g., 22:00-06:00).
                    if (!currentTime.isBefore(startTime) && currentTime.isBefore(endTime)) {
                        return "Skipped: Current time " + currentTime + " is within excluded range " + range + ".";
                    }
                } catch (java.time.format.DateTimeParseException e) {
                    logger.warn("Invalid time format in task_exclude_times for task ID {}: {}. Range: {}",
                            taskConfig.getTaskId(), e.getMessage(), range);
                }
            }
        }
        return null;
    }

    /**
     * Cancels a scheduled task by its ID.
     *
     * @param taskId The ID of the task to cancel.
     * @return {@code true} if the task was successfully cancelled,
     *         {@code false} otherwise (e.g., not found or already completed).
     */
    public boolean cancelTask(Integer taskId) {
        ScheduledFuture<?> future;
        synchronized (scheduledTasks) {
            future = scheduledTasks.remove(taskId);
            // cronTasks.remove(taskId); // Also remove from cronTasks map if it was being
            // used for registrar based scheduling
        }

        if (future != null) {
            boolean cancelled = future.cancel(true); // true to interrupt if running
            if (cancelled) {
                logger.info("Task {} cancelled successfully.", taskId);
            } else {
                logger.warn(
                        "Could not cancel task {}. It might have already completed, is non-interruptible, or cancel returned false.",
                        taskId);
            }
            return cancelled;
        } else {
            logger.warn("Task {} not found in scheduled tasks map, cannot cancel.", taskId);
            return false;
        }
    }

    /**
     * Reschedules a task. This typically involves cancelling the existing
     * scheduled task (if any) and then scheduling it again with the
     * (potentially updated) configuration.
     *
     * @param taskConfig The configuration of the task to reschedule.
     * @return {@code true} if the task was successfully rescheduled,
     *         {@code false} otherwise.
     */
    public boolean rescheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null) {
            logger.error("Cannot reschedule null task config.");
            return false;
        }
        logger.info("Attempting to reschedule task ID: {}", taskConfig.getTaskId());

        // Always cancel first, even if it's to change cron expression or activity
        // status
        boolean wasCancelled = cancelTask(taskConfig.getTaskId());
        if (wasCancelled) {
            logger.info("Task {} was running or scheduled and has been cancelled for rescheduling.",
                    taskConfig.getTaskId());
        } else {
            logger.info(
                    "Task {} was not actively scheduled (or couldn't be cancelled), attempting to schedule/reschedule.",
                    taskConfig.getTaskId());
        }

        // If the task is now active, schedule it. If not, it remains
        // cancelled/unscheduled.
        if (taskConfig.isActive()) {
            // Brief pause to ensure task is fully cancelled and resources potentially
            // released
            // This might not be strictly necessary depending on TaskScheduler
            // implementation, but can be a safeguard.
            // try { Thread.sleep(100); } catch (InterruptedException e) {
            // Thread.currentThread().interrupt(); }
            return scheduleTask(taskConfig);
        } else {
            logger.info("Task {} is inactive after attempted reschedule, it will not be scheduled.",
                    taskConfig.getTaskId());
            return true; // Considered success as the state (inactive) is achieved.
        }
    }

    /**
     * Triggers a task for immediate manual execution, regardless of its cron
     * schedule. The task's active status and other execution rules (locking,
     * exclusions) are still respected.
     *
     * @param taskId The ID of the task to trigger.
     * @throws IllegalArgumentException if the task is not found.
     */
    public void triggerTaskManually(Integer taskId) {
        // Default manual trigger: "NORMAL" pattern, no parent, no override parameters,
        // no workflow node ID.
        triggerTaskManually(taskId, ExecutionPattern.MANAUL.name(), null, null, null);
    }

    /**
     * Triggers a task for immediate execution with a specified initial pattern
     * and optional parent log ID.
     *
     * @param taskId         The ID of the task to trigger.
     * @param initialPattern The task pattern for the first attempt.
     * @param parentLogId    The parent log ID if this task is part of a workflow.
     * @throws IllegalArgumentException if the task is not found.
     */
    public void triggerTaskManually(Integer taskId, String initialPattern, Long parentLogId) {
        triggerTaskManually(taskId, initialPattern, parentLogId, null, null);
    }

    /**
     * Triggers a task for immediate execution with specified initial pattern,
     * optional parent log ID, and optional override bean parameters.
     *
     * @param taskId                      The ID of the task to trigger.
     * @param initialPattern              The task pattern for the first attempt.
     * @param parentLogId                 The parent log ID if this task is part of
     *                                    a workflow.
     * @param effectiveBeanParametersJson JSON string of bean parameters to use
     *                                    for this specific run.
     * @throws IllegalArgumentException if the task is not found.
     */
    public void triggerTaskManually(Integer taskId, String initialPattern, Long parentLogId,
            String effectiveBeanParametersJson) {
        triggerTaskManually(taskId, initialPattern, parentLogId, effectiveBeanParametersJson, null);
    }

    /**
     * Triggers a task for immediate execution with specified initial pattern,
     * optional parent log ID, optional override bean parameters, and optional
     * workflow node ID.
     *
     * @param taskId                      The ID of the task to trigger.
     * @param initialPattern              The task pattern for the first attempt
     *                                    (e.g.,
     *                                    "WORKFLOW_STEP").
     * @param parentLogId                 The parent log ID if this task is part of
     *                                    a workflow.
     * @param effectiveBeanParametersJson JSON string of bean parameters to use
     *                                    for this specific run, overriding stored
     *                                    ones.
     * @param workflowNodeId              The ID of the node in the workflow, if
     *                                    this task is
     *                                    a workflow step.
     * @throws IllegalArgumentException if the task is not found.
     */
    public void triggerTaskManually(Integer taskId, String initialPattern, Long parentLogId,
            String effectiveBeanParametersJson, String workflowNodeId) {
        TaskConfig taskConfig = taskConfigDao.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task not found with ID: " + taskId + " for manual trigger."));

        if (!taskConfig.isActive()) {
            // For now, allow triggering inactive tasks manually but log a warning.
            // Workflow steps, even if the underlying TaskConfig is marked inactive, might
            // need to run if the workflow is active.
            // This behavior might need refinement based on desired product logic for
            // inactive tasks within active workflows.
            logger.warn("Manual trigger requested for INACTIVE task ID: {}. Pattern: {}. It will attempt to run once.",
                    taskId, initialPattern);
        }

        // Create TaskExecutionJob with attemptNumber = 1 for manual trigger
        TaskExecutionJob job = new TaskExecutionJob(
                taskConfig,
                this.applicationContext,
                this.taskExecuteLogDao,
                this.distributedLockService,
                this, // Pass self for callback
                this.notificationService,
                this.distributedLockService.getSchedulerInstanceId(),
                1, // Initial attempt for a manual run
                initialPattern,
                parentLogId,
                effectiveBeanParametersJson,
                workflowNodeId);

        // Use the class field taskScheduler (ThreadPoolTaskScheduler)
        this.taskScheduler.schedule(job, Instant.now());
        logger.info(
                "Manually triggered task ID: {}. Pattern: {}. ParentLogID: {}. Attempt 1. Execution outcome will be logged.",
                taskId, initialPattern, parentLogId);
    }

    /**
     * Shuts down the scheduler service, cancelling all scheduled tasks. This is
     * called when the application context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down CoreSchedulerService. Cancelling all scheduled tasks ({}) and clearing maps.",
                scheduledTasks.size());
        synchronized (scheduledTasks) {
            scheduledTasks.keySet().forEach(this::cancelTask); // This already removes from scheduledTasks map
            // cronTasks.clear(); // Clear if it was used
        }

        // If the taskScheduler is an instance of ThreadPoolTaskScheduler, it might need
        // explicit shutdown.
        // However, Spring Boot usually manages the lifecycle of default TaskScheduler
        // beans.
        if (this.taskScheduler instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) {
            logger.info("Attempting to shut down internal ThreadPoolTaskScheduler.");
            ((org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) this.taskScheduler).shutdown();
        }
        // Also, if taskRegistrar was used and has its own scheduler:
        if (this.taskRegistrar != null && this.taskRegistrar.getScheduler() != null
                && this.taskRegistrar
                        .getScheduler() instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) {
            logger.info("Attempting to shut down taskRegistrar's ThreadPoolTaskScheduler.");
            ((org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) this.taskRegistrar.getScheduler())
                    .shutdown();
        }
        logger.info("CoreSchedulerService shutdown complete.");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        loadAndScheduleInitialTasks();
    }
}
