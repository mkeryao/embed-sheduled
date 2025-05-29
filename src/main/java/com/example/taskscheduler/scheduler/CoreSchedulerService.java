package com.example.taskscheduler.scheduler;

import com.example.taskscheduler.dao.TaskCalendarDao;
import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskCalendarDay;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.service.BeanTaskExecutor;
import com.example.taskscheduler.service.DistributedLockService;
import com.example.taskscheduler.service.NotificationService;
import com.example.taskscheduler.service.WorkflowExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
// import org.springframework.scheduling.support.CronTrigger; // Replaced by CustomTaskTrigger
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
// import java.util.UUID; // No longer used for instanceId directly here
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Core service responsible for managing the lifecycle of scheduled tasks.
 * It implements {@link SchedulingConfigurer} to dynamically register and manage tasks
 * based on configurations stored in the database. It handles scheduling, cancellation,
 * manual triggering, and integrates with other services for lock management,
 * task execution, and notifications.
 */
@Service
public class CoreSchedulerService implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CoreSchedulerService.class);
    // private final String instanceId = UUID.randomUUID().toString(); // Instance ID now comes from DistributedLockService

    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    @Autowired
    private TaskScheduler taskScheduler; // Spring's default TaskScheduler
    @Autowired
    private ApplicationContext applicationContext; // To get BeanTaskExecutor
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private TaskCalendarDao taskCalendarDao;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private WorkflowExecutionService workflowExecutionService;


    private BeanTaskExecutor beanTaskExecutor; // Lazily initialized

    // Keep track of scheduled tasks
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Integer, CronTask> cronTasks = new ConcurrentHashMap<>(); // For potential re-registration needs
    private ScheduledTaskRegistrar taskRegistrar;


    /**
     * Initializes the service after dependency injection.
     * It loads and schedules all active tasks from the database.
     */
    @PostConstruct
    public void init() {
        // beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class); // Initialize if needed immediately, or on first use
        loadAndScheduleInitialTasks();
    }

    /**
     * Configures tasks with the Spring task registrar.
     * This method is part of the {@link SchedulingConfigurer} interface.
     * @param taskRegistrar The registrar for scheduled tasks.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        // Initial tasks are typically loaded via @PostConstruct calling scheduleTask directly.
        // This registrar could be used if tasks were defined statically or needed more complex registrar-level setup.
    }

    /**
     * Loads all active tasks from the database and schedules them.
     * This is typically called on application startup.
     */
    public void loadAndScheduleInitialTasks() {
        logger.info("Loading and scheduling initial tasks...");
        List<TaskConfig> activeTasks = taskConfigDao.findAllActiveTasks();
        activeTasks.forEach(this::scheduleTask); // `this::scheduleTask` implicitly uses the class's taskScheduler
        logger.info("Scheduled {} initial tasks from a list of {} active tasks found.", scheduledTasks.size(), activeTasks.size());
    }

    /**
     * Schedules a single task based on its configuration.
     * If the task is already scheduled, it will not be scheduled again unless cancelled first.
     *
     * @param taskConfig The configuration of the task to schedule.
     * @return {@code true} if the task was successfully scheduled, {@code false} otherwise (e.g., inactive, invalid cron).
     */
    public boolean scheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null || !taskConfig.isActive()) {
            logger.warn("Task config is null or inactive, cannot schedule: Task ID {}", taskConfig != null ? taskConfig.getTaskId() : "null");
            return false;
        }
        // Prevent duplicate scheduling if already present and not cancelled.
        synchronized (scheduledTasks) {
            if (scheduledTasks.containsKey(taskConfig.getTaskId())) {
                logger.warn("Task {} ({}) is already scheduled. To reschedule, cancel it first or use rescheduleTask().",
                        taskConfig.getTaskId(), taskConfig.getTaskName());
                return false; // Or handle as an update if cron changed, but rescheduleTask is better for that
            }
        }

        Runnable taskRunnable = createTaskRunnable(taskConfig);
        if (taskRunnable == null) { // Should not happen if taskConfig is valid
            logger.error("Could not create runnable for task: {} (ID: {})", taskConfig.getTaskName(), taskConfig.getTaskId());
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
        } catch (IllegalArgumentException e) { // This might now be caught earlier by CronTrigger constructor if cron is invalid
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
     * Creates a {@link Runnable} for the given task configuration.
     * This runnable encapsulates the logic for executing the task, including
     * pre-execution checks (locking, date/time exclusions), actual execution,
     * logging, and notifications.
     *
     * @param taskConfig The configuration of the task.
     * @return A {@link Runnable} that can be scheduled.
     */
    private Runnable createTaskRunnable(TaskConfig taskConfig) {
        return () -> {
            TaskExecuteLog savedLog = null; // Initialize to null
            boolean lockAcquired = false;
            String taskLockName = taskConfig.getTaskLockName();
            String executeNo = null; // Will be logId
            
            try {
                // Initial log entry for attempting to run
                TaskExecuteLog log = new TaskExecuteLog();
                log.setTaskId(taskConfig.getTaskId());
                log.setStartTime(new Timestamp(System.currentTimeMillis()));
                log.setState("RUNNING"); // Initial state, might change to SKIPPED
                log.setInstanceId(distributedLockService.getSchedulerInstanceId());
                // Set taskPattern based on task type
                log.setTaskPattern(taskConfig.getTaskType() == 3 ? "WORKFLOW_PARENT" : "NORMAL");
                savedLog = taskExecuteLogDao.save(log);
                executeNo = String.valueOf(savedLog.getLogId());
                MDC.put("execute_no", executeNo);

                logger.info("Preparing to execute task: {} (ID: {}, Log ID: {})",
                        taskConfig.getTaskName(), taskConfig.getTaskId(), savedLog.getLogId());

                // Distributed Lock Acquisition
                if (StringUtils.hasText(taskLockName)) {
                    Integer lockSeconds = taskConfig.getTaskLockMostSeconds();
                    int secondsToLock = (lockSeconds != null && lockSeconds > 0) ? lockSeconds : 0; // 0 for "indefinite"
                    lockAcquired = distributedLockService.tryLock(taskLockName, distributedLockService.getSchedulerInstanceId(), secondsToLock);
                    if (!lockAcquired) {
                        String skipMessage = "Skipped: Could not acquire lock '" + taskLockName + "'";
                        logger.warn("{} for task ID {}", skipMessage, taskConfig.getTaskId());
                        taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SKIPPED", skipMessage);
                        notificationService.sendNotification(taskConfig, taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog));
                        return; 
                    }
                    logger.info("Lock '{}' acquired for task ID {}", taskLockName, taskConfig.getTaskId());
                }

                // Exclusion Checks (Date, Calendar, Time)
                String skipReason = checkDateExclusions(taskConfig);
                if (skipReason == null) skipReason = checkCalendarExclusions(taskConfig);
                if (skipReason == null) skipReason = checkTimeExclusions(taskConfig);

                if (skipReason != null) {
                    logger.info("Task {} (ID: {}) skipped: {}. Log ID: {}",
                            taskConfig.getTaskName(), taskConfig.getTaskId(), skipReason, savedLog.getLogId());
                    taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SKIPPED", skipReason);
                    // Notification for skipped tasks is handled in the finally block after lock release
                    return; 
                }

                // Actual Task Execution
                logger.info("Executing task: {} (ID: {}, Log ID: {})",
                        taskConfig.getTaskName(), taskConfig.getTaskId(), savedLog.getLogId());
                
                switch (taskConfig.getTaskType()) {
                    case 0: // Bean task
                        if (beanTaskExecutor == null) beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
                            beanTaskExecutor.execute(taskConfig);
                        TaskExecuteLog currentLogStateBean = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog);
                            if ("RUNNING".equals(currentLogStateBean.getState())) {
                            taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SUCCESS", null);
                        }
                        logger.info("Bean Task {} (ID: {}) completed. Final state: {}", taskConfig.getTaskName(), taskConfig.getTaskId(), taskExecuteLogDao.findById(savedLog.getLogId()).map(TaskExecuteLog::getState).orElse("UNKNOWN"));
                        break;
                        case 1: // Shell script task
                            // Ensure ShellTaskExecutor is bean-managed and autowired if not already
                            ShellTaskExecutor shellTaskExecutor = applicationContext.getBean(ShellTaskExecutor.class);
                            shellTaskExecutor.execute(taskConfig, savedLog);
                            logger.info("Shell Task {} (ID: {}) execution handled by ShellTaskExecutor. Final state: {}", taskConfig.getTaskName(), taskConfig.getTaskId(), taskExecuteLogDao.findById(savedLog.getLogId()).map(TaskExecuteLog::getState).orElse("UNKNOWN"));
                            break;
                        case 2: // HTTP task
                            HttpTaskExecutor httpTaskExecutor = applicationContext.getBean(HttpTaskExecutor.class);
                            httpTaskExecutor.execute(taskConfig, savedLog);
                            logger.info("HTTP Task {} (ID: {}) execution handled by HttpTaskExecutor. Final state: {}", taskConfig.getTaskName(), taskConfig.getTaskId(), taskExecuteLogDao.findById(savedLog.getLogId()).map(TaskExecuteLog::getState).orElse("UNKNOWN"));
                            break;
                    case 3: // Workflow task
                        workflowExecutionService.startWorkflow(taskConfig, savedLog);
                        logger.info("Workflow Task {} (ID: {}) processing initiated. Final state will be set by WorkflowExecutionService.", taskConfig.getTaskName(), taskConfig.getTaskId());
                        break;
                        default: 
                            String unknownMsg = "Unknown task type: " + taskConfig.getTaskType();
                            logger.error(unknownMsg + " for task ID: {}", taskConfig.getTaskId());
                            taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", unknownMsg);
                        break;
                }
            } catch (BeanTaskExecutor.TaskTimeoutException e) { 
                logger.error("Task {} (ID: {}) timed out.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                if (savedLog != null) taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "TIMED_OUT", e.getMessage());
            } catch (Exception e) {
                logger.error("Task {} (ID: {}) failed with an unexpected exception.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                if (savedLog != null) {
                    String errorMsg = e.getMessage() != null ? (e.getMessage().length() > 2000 ? e.getMessage().substring(0, 2000) : e.getClass().getSimpleName()) : "Unknown error";
                    TaskExecuteLog currentLog = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(null);
                    // Avoid overwriting a more specific state like TIMED_OUT if already set by BeanTaskExecutor's exception handling
                    if (currentLog != null && !"TIMED_OUT".equals(currentLog.getState())) { 
                        taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", errorMsg);
                    } else if (currentLog == null) { // Should ideally not happen
                         taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", "Log disappeared: " + errorMsg);
                    }
                }
            } finally {
                if (lockAcquired && StringUtils.hasText(taskLockName)) {
                    distributedLockService.unlock(taskLockName, distributedLockService.getSchedulerInstanceId());
                    logger.info("Lock '{}' released for task ID {}", taskLockName, taskConfig.getTaskId());
                }
                // Send notification after final log state is set (or determined)
                if (savedLog != null && savedLog.getLogId() != null) {
                    TaskExecuteLog finalLogState = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog); // Refresh log state
                    notificationService.sendNotification(taskConfig, finalLogState);
                } else {
                    // This case might occur if the initial log save itself failed.
                    logger.error("Could not send notification for task {} (ID: {}) because its execution log was not properly saved.", taskConfig.getTaskName(), taskConfig.getTaskId());
                }
            }
        };
    }

    // --- Helper methods for exclusion checks ---

    /**
     * Checks if the task should be excluded based on its configured start and end dates.
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
     * Checks if the task should be excluded based on a configured task calendar.
     * If a {@code taskCalendarGroup} is specified, it checks if the current date
     * is marked as a non-working day in that calendar.
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
                    .map(nonWorkingDay -> "Skipped: Current date " + today + " is a non-working day (" + nonWorkingDay.getDescription() + ") in calendar group '" + taskConfig.getTaskCalendarGroup() + "'.");
            })
            .orElse(null); 
    }

    /**
     * Checks if the task should be excluded based on configured time ranges for the current day.
     * Time ranges are specified in {@code taskExcludeTimes} like "HH:mm-HH:mm,HH:mm-HH:mm".
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
                    logger.warn("Invalid time format in task_exclude_times for task ID {}: {}. Range: {}", taskConfig.getTaskId(), e.getMessage(), range);
                }
            }
        }
        return null;
    }

    /**
     * Cancels a scheduled task by its ID.
     *
     * @param taskId The ID of the task to cancel.
     * @return {@code true} if the task was successfully cancelled, {@code false} otherwise (e.g., not found or already completed).
     */
    public boolean cancelTask(Integer taskId) {
        ScheduledFuture<?> future;
        synchronized (scheduledTasks) {
             future = scheduledTasks.remove(taskId);
             // cronTasks.remove(taskId); // Also remove from cronTasks map if it was being used for registrar based scheduling
        }

        if (future != null) {
            boolean cancelled = future.cancel(true); // true to interrupt if running
            if (cancelled) {
                logger.info("Task {} cancelled successfully.", taskId);
            } else {
                logger.warn("Could not cancel task {}. It might have already completed, is non-interruptible, or cancel returned false.", taskId);
            }
            return cancelled;
        } else {
            logger.warn("Task {} not found in scheduled tasks map, cannot cancel.", taskId);
            return false;
        }
    }

    /**
     * Reschedules a task. This typically involves cancelling the existing scheduled task
     * (if any) and then scheduling it again with the (potentially updated) configuration.
     *
     * @param taskConfig The configuration of the task to reschedule.
     * @return {@code true} if the task was successfully rescheduled, {@code false} otherwise.
     */
    public boolean rescheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null) {
            logger.error("Cannot reschedule null task config.");
            return false;
        }
        logger.info("Attempting to reschedule task ID: {}", taskConfig.getTaskId());
        
        // Always cancel first, even if it's to change cron expression or activity status
        boolean wasCancelled = cancelTask(taskConfig.getTaskId()); 
        if (wasCancelled) {
             logger.info("Task {} was running or scheduled and has been cancelled for rescheduling.", taskConfig.getTaskId());
        } else {
            logger.info("Task {} was not actively scheduled (or couldn't be cancelled), attempting to schedule/reschedule.", taskConfig.getTaskId());
        }

        // If the task is now active, schedule it. If not, it remains cancelled/unscheduled.
        if (taskConfig.isActive()) {
            // Brief pause to ensure task is fully cancelled and resources potentially released
            // This might not be strictly necessary depending on TaskScheduler implementation, but can be a safeguard.
            // try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return scheduleTask(taskConfig);
        } else {
            logger.info("Task {} is inactive after attempted reschedule, it will not be scheduled.", taskConfig.getTaskId());
            return true; // Considered success as the state (inactive) is achieved.
        }
    }

    /**
     * Triggers a task for immediate manual execution, regardless of its cron schedule.
     * The task's active status and other execution rules (locking, exclusions) are still respected.
     *
     * @param taskId The ID of the task to trigger.
     * @throws IllegalArgumentException if the task is not found.
     */
    public void triggerTaskManually(Integer taskId) {
        TaskConfig taskConfig = taskConfigDao.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId + " for manual trigger."));
        
        // Log if triggering an inactive task, but still proceed as manual trigger implies override of schedule.
        // The execution runnable itself will check isActive for regular scheduling, but manual trigger might bypass this.
        // However, the current createTaskRunnable respects exclusions.
        if (!taskConfig.isActive()) {
            logger.warn("Manual trigger requested for INACTIVE task ID: {}. It will attempt to run once if other conditions pass.", taskId);
        }

        Runnable runnable = createTaskRunnable(taskConfig); 
        if (runnable != null) {
            // Task is run in a thread from the taskScheduler's pool
            taskScheduler.schedule(runnable, Instant.now());
            logger.info("Manually triggered task ID: {}. Execution outcome will be logged by the task itself.", taskId);
        } else {
            // This should not happen if taskConfig is valid.
            logger.error("Could not create runnable for manual trigger of task ID: {}. Logging a FAILED log.", taskId);
            TaskExecuteLog log = new TaskExecuteLog();
            log.setTaskId(taskId);
            log.setStartTime(new Timestamp(System.currentTimeMillis()));
            log.setState("FAILED");
            log.setExMsg("Failed to create runnable for manual trigger");
            log.setInstanceId(distributedLockService.getSchedulerInstanceId());
            log.setTaskPattern(taskConfig.getTaskType() == 3 ? "WORKFLOW_PARENT" : "NORMAL"); // Set pattern for consistency
            taskExecuteLogDao.save(log); // No notification for this pre-flight failure
        }
    }

    /**
     * Shuts down the scheduler service, cancelling all scheduled tasks.
     * This is called when the application context is destroyed.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down CoreSchedulerService. Cancelling all scheduled tasks ({}) and clearing maps.", scheduledTasks.size());
        synchronized (scheduledTasks) {
            scheduledTasks.keySet().forEach(this::cancelTask); // This already removes from scheduledTasks map
            // cronTasks.clear(); // Clear if it was used
        }
        
        // If the taskScheduler is an instance of ThreadPoolTaskScheduler, it might need explicit shutdown.
        // However, Spring Boot usually manages the lifecycle of default TaskScheduler beans.
        if (this.taskScheduler instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) {
            logger.info("Attempting to shut down internal ThreadPoolTaskScheduler.");
            ((org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) this.taskScheduler).shutdown();
        }
        // Also, if taskRegistrar was used and has its own scheduler:
        if (this.taskRegistrar != null && this.taskRegistrar.getScheduler() != null &&
            this.taskRegistrar.getScheduler() instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) {
             logger.info("Attempting to shut down taskRegistrar's ThreadPoolTaskScheduler.");
            ((org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) this.taskRegistrar.getScheduler()).shutdown();
        }
        logger.info("CoreSchedulerService shutdown complete.");
    }
}
