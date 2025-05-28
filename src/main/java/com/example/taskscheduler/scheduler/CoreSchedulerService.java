package com.example.taskscheduler.scheduler;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dao.TaskCalendarDao;
import com.example.taskscheduler.entity.TaskCalendarDay;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.service.BeanTaskExecutor;
import com.example.taskscheduler.service.DistributedLockService;
import com.example.taskscheduler.service.NotificationService;
import com.example.taskscheduler.service.WorkflowExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class CoreSchedulerService implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CoreSchedulerService.class);
    private final String instanceId = UUID.randomUUID().toString();

    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private TaskScheduler taskScheduler; // Spring's default TaskScheduler

    @Autowired
    private ApplicationContext applicationContext;

    private BeanTaskExecutor beanTaskExecutor;

    @Autowired
    private DistributedLockService distributedLockService;

    @Autowired
    private TaskCalendarDao taskCalendarDao; // For calendar checks

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WorkflowExecutionService workflowExecutionService;


    // Keep track of scheduled tasks
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Integer, CronTask> cronTasks = new ConcurrentHashMap<>(); // For re-registering
    private ScheduledTaskRegistrar taskRegistrar;


    @PostConstruct
    public void init() {
        beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
        loadAndScheduleInitialTasks();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        this.taskRegistrar = taskRegistrar;
        // Initial tasks are loaded via @PostConstruct and scheduleTask method
        // which now uses this taskRegistrar if available, or direct scheduling if not.
    }

    public void loadAndScheduleInitialTasks() {
        logger.info("Loading and scheduling initial tasks...");
        List<TaskConfig> activeTasks = taskConfigDao.findAllActiveTasks();
        activeTasks.forEach(this::scheduleTask);
        logger.info("Scheduled {} initial tasks.", scheduledTasks.size());
    }

    public boolean scheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null || !taskConfig.isActive()) {
            logger.warn("Task config is null or inactive, cannot schedule: {}", taskConfig != null ? taskConfig.getTaskId() : "null");
            return false;
        }
        if (scheduledTasks.containsKey(taskConfig.getTaskId())) {
            logger.warn("Task {} is already scheduled. Cancel and reschedule if needed.", taskConfig.getTaskId());
            return false;
        }

        Runnable taskRunnable = createTaskRunnable(taskConfig);
        if (taskRunnable == null) {
            logger.error("Could not create runnable for task: {}", taskConfig.getTaskName());
            return false;
        }

        try {
            CronTrigger cronTrigger = new CronTrigger(taskConfig.getCronExpression());
            ScheduledFuture<?> future;
            if (this.taskRegistrar != null && this.taskRegistrar.getScheduler() != null) {
                 // If SchedulingConfigurer is active, use its registrar
                CronTask cronTask = new CronTask(taskRunnable, cronTrigger);
                this.taskRegistrar.addCronTask(cronTask); // This doesn't return a future directly for cancellation in the same way
                                                          // We might need a different approach if using registrar for dynamic add/remove
                                                          // For now, we'll store the CronTask itself for potential removal/re-add
                // This is a limitation: taskRegistrar.addCronTask does not return a ScheduledFuture directly.
                // To manage cancellation, we'd typically use the TaskScheduler directly.
                // Let's stick to direct TaskScheduler for dynamic management for now.
                // If using taskRegistrar, management is more about initial setup or complete re-configuration.
                // For dynamic add/remove, direct use of taskScheduler is more straightforward.
                 future = taskScheduler.schedule(taskRunnable, cronTrigger);
                 cronTasks.put(taskConfig.getTaskId(), cronTask); // Store for potential re-registration logic
            } else {
                // Fallback or default direct scheduling
                future = taskScheduler.schedule(taskRunnable, cronTrigger);
            }
            
            scheduledTasks.put(taskConfig.getTaskId(), future);
            logger.info("Task {} ({}) scheduled successfully with cron: {}", taskConfig.getTaskId(), taskConfig.getTaskName(), taskConfig.getCronExpression());
            return true;
        } catch (IllegalArgumentException e) {
            logger.error("Invalid cron expression for task {}: {}", taskConfig.getTaskId(), taskConfig.getCronExpression(), e);
            return false;
        } catch (Exception e) {
            logger.error("Failed to schedule task {}: {}", taskConfig.getTaskId(), taskConfig.getTaskName(), e);
            return false;
        }
    }

    private Runnable createTaskRunnable(TaskConfig taskConfig) {
        return () -> {
            TaskExecuteLog log = new TaskExecuteLog();
            log.setTaskId(taskConfig.getTaskId());
            log.setStartTime(new Timestamp(System.currentTimeMillis()));
            log.setState("RUNNING");
            log.setInstanceId(distributedLockService.getSchedulerInstanceId()); // Use instance ID from lock service
            TaskExecuteLog savedLog = taskExecuteLogDao.save(log); 
            logger.info("Preparing to execute task: {} (ID: {}, Log ID: {})", taskConfig.getTaskName(), taskConfig.getTaskId(), savedLog.getLogId());
            
            String skipReason = null;
            boolean lockAcquired = false;
            String taskLockName = taskConfig.getTaskLockName();
            Integer lockSeconds = taskConfig.getTaskLockMostSeconds();

            try {
                if (taskLockName != null && !taskLockName.trim().isEmpty()) {
                    int secondsToLock = (lockSeconds != null && lockSeconds > 0) ? lockSeconds : 0; // 0 for "indefinite" by DistributedLockService logic
                    lockAcquired = distributedLockService.tryLock(taskLockName, distributedLockService.getSchedulerInstanceId(), secondsToLock);
                    if (!lockAcquired) {
                        String skipMessage = "Skipped: Could not acquire lock '" + taskLockName + "'";
                        logger.warn(skipMessage + " for task ID {}", taskConfig.getTaskId());
                        taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SKIPPED", skipMessage);
                        // Notification for skipped due to lock not acquired might be noisy, consider if needed
                        // notificationService.sendNotification(taskConfig, taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog));
                        return; 
                    }
                    logger.info("Lock '{}' acquired for task ID {}", taskLockName, taskConfig.getTaskId());
                }

                // === Start/End Date, Calendar, and Time Exclusion Checks ===
                skipReason = checkDateExclusions(taskConfig);
                if (skipReason == null) {
                    skipReason = checkCalendarExclusions(taskConfig);
                }
                if (skipReason == null) {
                    skipReason = checkTimeExclusions(taskConfig);
                }

                if (skipReason != null) {
                    logger.info("Task {} (ID: {}) skipped: {}. Log ID: {}", taskConfig.getTaskName(), taskConfig.getTaskId(), skipReason, savedLog.getLogId());
                    taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SKIPPED", skipReason);
                    notificationService.sendNotification(taskConfig, taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog));
                    return; // Do not execute
                }
                // === End Exclusion Checks ===


                // Proceed with execution if no lock needed or lock acquired, and not excluded
                logger.info("Executing task: {} (ID: {}, Log ID: {})", taskConfig.getTaskName(), taskConfig.getTaskId(), savedLog.getLogId());
                try {
                    // Initialize log pattern if not already set (e.g. for manual trigger)
                    if (savedLog.getTaskPattern() == null) {
                        savedLog.setTaskPattern("NORMAL"); // Default pattern
                        // taskExecuteLogDao.update(savedLog); // Persist if necessary, or ensure save() handles it
                    }

                    switch (taskConfig.getTaskType()) {
                        case 0: // Bean task
                            if (beanTaskExecutor == null) beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
                            beanTaskExecutor.execute(taskConfig);
                            TaskExecuteLog currentLogStateBean = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog);
                            if ("RUNNING".equals(currentLogStateBean.getState())) {
                                taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "SUCCESS", null);
                            }
                            logger.info("Bean Task {} (ID: {}) completed. Final state: {}", taskConfig.getTaskName(), taskConfig.getTaskId(), taskExecuteLogDao.findById(savedLog.getLogId()).get().getState());
                            break;
                        case 1: // HTTP task - Not Implemented
                        case 2: // Shell script - Not Implemented
                            String NImessage = "Task type " + taskConfig.getTaskType() + " not implemented yet.";
                            logger.warn(NImessage + " for task ID: {}", taskConfig.getTaskId());
                            taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", NImessage);
                            // throw new UnsupportedOperationException(NImessage);
                            break;
                        case 3: // Workflow task
                            // The savedLog is the parent log for the workflow
                            savedLog.setTaskPattern("WORKFLOW_PARENT");
                            taskExecuteLogDao.update(savedLog); // Persist pattern change

                            workflowExecutionService.startWorkflow(taskConfig, savedLog);
                            // WorkflowExecutionService is responsible for updating savedLog's status (SUCCESS/FAILED) based on workflow outcome.
                            logger.info("Workflow Task {} (ID: {}) processing initiated. Final state will be set by WorkflowExecutionService.", taskConfig.getTaskName(), taskConfig.getTaskId());
                            break;
                        default:
                            String unknownMsg = "Unknown task type: " + taskConfig.getTaskType();
                            logger.error(unknownMsg + " for task ID: {}", taskConfig.getTaskId());
                            taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", unknownMsg);
                            // throw new IllegalArgumentException(unknownMsg);
                            break;
                    }
                } catch (BeanTaskExecutor.TaskTimeoutException e) { // Specific catch for timeout from BeanTaskExecutor
                    logger.error("Task {} (ID: {}) timed out.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                    taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "TIMED_OUT", e.getMessage());
                } catch (Exception e) {
                    logger.error("Task {} (ID: {}) failed.", taskConfig.getTaskName(), taskConfig.getTaskId(), e);
                    String errorMsg = e.getMessage() != null ? (e.getMessage().length() > 2000 ? e.getMessage().substring(0, 2000) : e.getClass().getSimpleName()) : "Unknown error";
                    TaskExecuteLog currentLog = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(null);
                    if (currentLog != null && !"TIMED_OUT".equals(currentLog.getState())) { // Avoid overwriting TIMED_OUT if already set
                        taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", errorMsg);
                    } else if (currentLog == null) {
                         taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", "Log disappeared: " + errorMsg); // Should not happen
                    }
                } finally {
                    if (lockAcquired && taskLockName != null && !taskLockName.trim().isEmpty()) {
                        distributedLockService.unlock(taskLockName, distributedLockService.getSchedulerInstanceId());
                        logger.info("Lock '{}' released for task ID {}", taskLockName, taskConfig.getTaskId());
                    }
                    // For bean tasks, notification is sent here. For workflows, it's sent by WorkflowExecutionService or here based on final parent log.
                    TaskExecuteLog finalLogState = taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog);
                    // Avoid double notification if workflow service handles it.
                    // Workflow service will update the parent log, then we notify based on that.
                    // If task type is WORKFLOW, its final state is set by WorkflowExecutionService.
                    // We can send notification for WORKFLOW_PARENT here after it's finished.
                    notificationService.sendNotification(taskConfig, finalLogState);
                }
            } catch (Exception outerEx) {
                logger.error("Outer exception for task {} (ID: {}): {}", taskConfig.getTaskName(), taskConfig.getTaskId(), outerEx.getMessage(), outerEx);
                String errorMsg = outerEx.getMessage() != null ? (outerEx.getMessage().length() > 2000 ? outerEx.getMessage().substring(0, 2000) : outerEx.getClass().getSimpleName()) : "Unknown outer error";
                if (savedLog != null && savedLog.getLogId() != null) { // Ensure log exists before trying to update
                    taskExecuteLogDao.updateLogStatus(savedLog.getLogId(), "FAILED", "Outer error: " + errorMsg);
                    notificationService.sendNotification(taskConfig, taskExecuteLogDao.findById(savedLog.getLogId()).orElse(savedLog));
                } else {
                     logger.error("Cannot update log status for outer exception as savedLog or logId is null. TaskConfig ID: {}", taskConfig.getTaskId());
                }
            }
        };
    }


    // --- Helper methods for exclusion checks ---

    private String checkDateExclusions(TaskConfig taskConfig) {
        java.util.Date now = new java.util.Date(); // Current date and time
        if (taskConfig.getStartDate() != null && taskConfig.getStartDate().after(now)) {
            return "Skipped: Start date " + taskConfig.getStartDate() + " is in the future.";
        }
        if (taskConfig.getEndDate() != null && taskConfig.getEndDate().before(now)) {
            // Optionally, could also unschedule the task here if it's definitively past its end_date
            // coreSchedulerService.cancelTask(taskConfig.getTaskId());
            // taskConfigDao.updateTaskStatus(taskConfig.getTaskId(), false);
            return "Skipped: End date " + taskConfig.getEndDate() + " is in the past.";
        }
        return null;
    }

    private String checkCalendarExclusions(TaskConfig taskConfig) {
        if (taskConfig.getTaskCalendarGroup() == null || taskConfig.getTaskCalendarGroup().trim().isEmpty()) {
            return null; // No calendar group specified
        }
        // Assuming TaskCalendarDao.findCalendarByName gets the calendar by its unique name (group name)
        return taskCalendarDao.findCalendarByName(taskConfig.getTaskCalendarGroup())
            .flatMap(calendar -> {
                // Check if today is a non-working day in this calendar
                // java.sql.Date.valueOf(java.time.LocalDate.now()) is one way to get current date as sql.Date
                java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                return taskCalendarDao.findCalendarDayByCalendarIdAndDate(calendar.getCalendarId(), today)
                    .filter(calendarDay -> !calendarDay.isWorkingDay()) // If it's NOT a working day
                    .map(nonWorkingDay -> "Skipped: Current date " + today + " is a non-working day (" + nonWorkingDay.getDescription() + ") in calendar group '" + taskConfig.getTaskCalendarGroup() + "'.");
            })
            .orElse(null); // No exclusion if calendar/day not found or if it's a working day
    }

    private String checkTimeExclusions(TaskConfig taskConfig) {
        if (taskConfig.getTaskExcludeTimes() == null || taskConfig.getTaskExcludeTimes().trim().isEmpty()) {
            return null; // No time exclusions specified
        }
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        String[] ranges = taskConfig.getTaskExcludeTimes().split(",");
        for (String range : ranges) {
            String[] times = range.trim().split("-");
            if (times.length == 2) {
                try {
                    java.time.LocalTime startTime = java.time.LocalTime.parse(times[0].trim());
                    java.time.LocalTime endTime = java.time.LocalTime.parse(times[1].trim());

                    // Handle overnight ranges if necessary, though typically exclusion is within a day
                    // For simplicity, assuming ranges are within the same day (e.g., 22:00-06:00 is not directly supported by this simple check)
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



    public boolean cancelTask(Integer taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true); // true to interrupt running task
            if (cancelled) {
                scheduledTasks.remove(taskId);
                cronTasks.remove(taskId); // Also remove from cronTasks map
                logger.info("Task {} cancelled successfully.", taskId);
                // Optionally update task_config to set is_active = false
                // taskConfigDao.updateTaskStatus(taskId, false);
            } else {
                logger.warn("Could not cancel task {}. It might have already completed or is non-interruptible.", taskId);
            }
            return cancelled;
        } else {
            logger.warn("Task {} not found in scheduled tasks, cannot cancel.", taskId);
            return false;
        }
    }

    public boolean rescheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null) {
            logger.error("Cannot reschedule null task config.");
            return false;
        }
        logger.info("Attempting to reschedule task ID: {}", taskConfig.getTaskId());
        if (cancelTask(taskConfig.getTaskId())) {
            // Brief pause to ensure task is fully cancelled before rescheduling
            try {
                Thread.sleep(100); // Small delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return scheduleTask(taskConfig);
        } else {
            // If not cancelled (e.g. not found), try scheduling it directly if it's active
            if (taskConfig.isActive()) {
                 logger.info("Task {} was not previously scheduled or could not be cancelled. Attempting to schedule it now.", taskConfig.getTaskId());
                 return scheduleTask(taskConfig);
            } else {
                logger.warn("Task {} could not be cancelled and is inactive. Not scheduling.", taskConfig.getTaskId());
                return false;
            }
        }
    }

    public void triggerTaskManually(Integer taskId) {
        TaskConfig taskConfig = taskConfigDao.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!taskConfig.isActive()) {
            logger.warn("Manual trigger for inactive task ID: {}. Task will run once but won't be scheduled if inactive.", taskId);
        }
        // Manual trigger should also respect locking
        Runnable runnable = createTaskRunnable(taskConfig); // This runnable now includes locking logic
        if (runnable != null) {
            // Run it in a separate thread from the taskScheduler's pool
            // The runnable itself will handle logging of RUNNING, SUCCESS/FAILED/SKIPPED state
            taskScheduler.schedule(runnable, Instant.now());
            logger.info("Manually triggered task ID: {}. Execution outcome will be logged by the task itself.", taskId);
        } else {
            // This case should ideally not happen if taskConfig is valid
            logger.error("Could not create runnable for manual trigger of task ID: {}. Logging a FAILED log.", taskId);
            TaskExecuteLog log = new TaskExecuteLog();
            log.setTaskId(taskId);
            log.setStartTime(new Timestamp(System.currentTimeMillis()));
            log.setState("FAILED");
            log.setExMsg("Failed to create runnable for manual trigger");
            log.setInstanceId(distributedLockService.getSchedulerInstanceId());
            taskExecuteLogDao.save(log);
        }
    }


    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down CoreSchedulerService. Cancelling all scheduled tasks.");
        scheduledTasks.keySet().forEach(this::cancelTask);
        scheduledTasks.clear();
        cronTasks.clear();
        if (this.taskRegistrar != null && this.taskRegistrar.getScheduler() != null) {
             // This is tricky, as ScheduledTaskRegistrar doesn't offer a clear "destroy all"
             // For ThreadPoolTaskScheduler, you'd call shutdown()
            if (this.taskRegistrar.getScheduler() instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) {
                ((org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler) this.taskRegistrar.getScheduler()).shutdown();
            }
        }
        logger.info("All tasks cancelled and scheduler shut down.");
    }
}
