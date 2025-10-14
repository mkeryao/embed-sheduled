package com.github.embed.scheduler.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.embed.scheduler.dao.TaskCalendarDao;
import com.github.embed.scheduler.dao.TaskConfigDao;
import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import com.github.embed.scheduler.service.DistributedLockService;
import com.github.embed.scheduler.service.NotificationService;
import com.github.embed.scheduler.service.WorkflowExecutionService;

/**
 * Core service responsible for managing the lifecycle of scheduled tasks.
 */
@Service
public class CoreSchedulerService implements SchedulingConfigurer, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(CoreSchedulerService.class);

    @Value("${scheduler.group.name:defaultGroup}")
    private String schedulerGroupName;

    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private TaskCalendarDao taskCalendarDao;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    @org.springframework.context.annotation.Lazy
    private WorkflowExecutionService workflowExecutionService;

    private ThreadPoolTaskScheduler taskScheduler;
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(15);
        threadPoolTaskScheduler.setThreadNamePrefix("core-scheduler-");
        threadPoolTaskScheduler.initialize();
        this.taskScheduler = threadPoolTaskScheduler;
        taskRegistrar.setScheduler(this.taskScheduler);
    }

    @Scheduled(cron = "${scheduler.sync.cron:0 0 0/1 * * *}")
    public void loadAndScheduleInitialTasks() {
        logger.info("Starting scheduler state synchronization for group: {}...", schedulerGroupName);
        List<TaskConfig> dbTasks = taskConfigDao.findAllActiveTasks(schedulerGroupName);
        Set<Integer> dbEnabledTaskIds = dbTasks.stream()
                .map(TaskConfig::getTaskId)
                .collect(Collectors.toSet());

        Set<Integer> scheduledJobIds;
        synchronized (scheduledTasks) {
            scheduledJobIds = scheduledTasks.keySet().stream().collect(Collectors.toSet());
        }

        for (TaskConfig dbTask : dbTasks) {
            if (!scheduledJobIds.contains(dbTask.getTaskId())) {
                logger.info("Sync: Found new or unscheduled task {}. Scheduling.", dbTask.getTaskId());
                scheduleTask(dbTask);
            }
        }

        for (Integer scheduledJobId : scheduledJobIds) {
            if (!dbEnabledTaskIds.contains(scheduledJobId)) {
                logger.info("Sync: Task {} is no longer active in DB. Unscheduling.", scheduledJobId);
                cancelTask(scheduledJobId);
            }
        }
        logger.info("Scheduler state synchronization completed. Current scheduled tasks: {}", scheduledTasks.size());
    }

    public void scheduleTask(TaskConfig taskConfig) {
        if (taskConfig == null || !taskConfig.isActive()) {
            logger.warn("Attempted to schedule a null or inactive task.");
            return;
        }

        if (scheduledTasks.containsKey(taskConfig.getTaskId())) {
            logger.info("Task {} is already scheduled. Cancelling and rescheduling.", taskConfig.getTaskId());
            cancelTask(taskConfig.getTaskId());
        }

        Runnable taskRunnable = createTaskRunnable(taskConfig, ExecutionPattern.NORMAL, null);
        
        try {
            CustomTaskTrigger customTaskTrigger = new CustomTaskTrigger(taskConfig, taskCalendarDao);
            ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(taskRunnable, customTaskTrigger);
            scheduledTasks.put(taskConfig.getTaskId(), scheduledFuture);
            logger.info("Task '{}' (ID: {}) with cron '{}' scheduled successfully using CustomTaskTrigger.", taskConfig.getTaskName(), taskConfig.getTaskId(), taskConfig.getCronExpression());
        } catch (Exception e) {
            logger.error("Failed to schedule task '{}' (ID: {}). Cron expression '{}' may be invalid.", taskConfig.getTaskName(), taskConfig.getTaskId(), taskConfig.getCronExpression(), e);
        }
    }

    public boolean cancelTask(Integer taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                scheduledTasks.remove(taskId);
                logger.info("Task {} cancelled successfully.", taskId);
            } else {
                logger.warn("Failed to cancel task {}. It might have already completed or is non-interruptible.", taskId);
            }
            return cancelled;
        } else {
            logger.warn("Could not cancel task {}. It was not found in the scheduled tasks map.", taskId);
            return false;
        }
    }

    public void triggerTaskManually(Integer taskId) {
        TaskConfig taskConfig = taskConfigDao.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        logger.info("Manually triggering task '{}' (ID: {})", taskConfig.getTaskName(), taskId);
        ExecutionPattern pattern = (taskConfig.getTaskType() == 10) ? ExecutionPattern.WORKFLOW_PARENT : ExecutionPattern.MANUAL;
        Runnable taskRunnable = createTaskRunnable(taskConfig, pattern, null);
        taskScheduler.execute(taskRunnable);
    }
    
    public void triggerWorkflowNodeTask(long logId) {
        TaskExecuteLog log = taskExecuteLogDao.findById(logId).orElse(null);
        if (log == null) {
            logger.error("Cannot trigger workflow node task, log not found for id: {}", logId);
            return;
        }
        TaskConfig taskConfig = taskConfigDao.findById(log.getTaskId()).orElse(null);
        if (taskConfig == null) {
            logger.error("Cannot trigger workflow node task, task config not found for id: {}", log.getTaskId());
            return;
        }
    
        // Prepare a temporary TaskConfig with overridden parameters for this specific run
        TaskConfig configForNodeRun = taskConfig;
        if (log.getParameters() != null && !log.getParameters().isEmpty()) {
            configForNodeRun = new TaskConfig();
            org.springframework.beans.BeanUtils.copyProperties(taskConfig, configForNodeRun);
            configForNodeRun.setParameters(log.getParameters());
        }

        logger.info("Asynchronously triggering execution for workflow node '{}' (Task ID: {}, Log ID: {})",
                log.getWorkflowNodeId(), log.getTaskId(), log.getId());
    
        TaskExecutionJob job = new TaskExecutionJob(
                configForNodeRun, // Pass the config with potentially overridden parameters
                this.applicationContext,
                this.taskExecuteLogDao,
                this.distributedLockService,
                this,
                this.notificationService,
                this.distributedLockService.getSchedulerInstanceId(),
                log.getAttempt() > 0 ? log.getAttempt() : 1, // Use attempt from log if it's a retry
                ExecutionPattern.WORKFLOW_STEP,
                log.getParentLogId() != null ? log.getParentLogId().longValue() : null,
                log.getParameters(), // Keep passing for logging/retry purposes
                log.getWorkflowNodeId(), // Correctly use the node ID from the log
                log.getId()
        );
    
        taskScheduler.execute(job);
    }

    public void handleTaskCompletion(long executionLogId) {
        TaskExecuteLog executionLog = taskExecuteLogDao.findById(executionLogId)
                .orElseThrow(() -> new IllegalStateException("Execution log not found for ID: " + executionLogId));

        TaskConfig taskConfig = taskConfigDao.findById(executionLog.getTaskId())
                .orElseThrow(() -> new IllegalStateException("Task config not found for ID: " + executionLog.getTaskId()));

        boolean isSuccess = executionLog.getState() == ExecutionState.SUCCESS;
        boolean isWorkflowStep = executionLog.getTaskPattern() == ExecutionPattern.WORKFLOW_STEP;

        if (isSuccess) {
            if (isWorkflowStep) {
                workflowExecutionService.handleNodeCompletion(executionLog.getId());
            }
            // Send success notification for regular tasks
            if (taskConfig.isSuccessNotification()) {
                notificationService.sendSuccessNotification(taskConfig, executionLog);
            }
        } else { // FAILED, TIMED_OUT, etc.
            int maxRetries = taskConfig.getMaxRetries();
            int currentAttempt = executionLog.getAttempt();
            if (currentAttempt < maxRetries) {
                scheduleRetry(taskConfig, executionLog);
            } else {
                logger.error("Task {} failed after reaching max retries ({}). No more retries.", taskConfig.getTaskId(), maxRetries);
                // Failure notification is sent for all failures after retries are exhausted
                if (taskConfig.isFailureNotification()) {
                    notificationService.sendFailureNotification(taskConfig, executionLog);
                }
                if (isWorkflowStep) {
                    workflowExecutionService.handleNodeFailure(executionLog.getId());
                }
            }
        }
    }

    private void scheduleRetry(TaskConfig taskConfig, TaskExecuteLog previousLog) {
        int nextAttempt = previousLog.getAttempt() + 1;
        long delay = calculateRetryDelay(nextAttempt, taskConfig.getRetryPolicy());
        logger.info("Scheduling retry {}/{} for task {} in {} ms.", nextAttempt, taskConfig.getMaxRetries(), taskConfig.getTaskId(), delay);

        TaskExecutionJob retryJob = new TaskExecutionJob(
                taskConfig,
                applicationContext,
                taskExecuteLogDao,
                distributedLockService,
                this,
                notificationService,
                distributedLockService.getSchedulerInstanceId(),
                nextAttempt,
                previousLog.getTaskPattern(),
                previousLog.getParentLogId() != null ? previousLog.getParentLogId().longValue() : null,
                previousLog.getParameters(),
                previousLog.getWorkflowNodeId(),
                null // A new log will be created, so no initial log id
        );

        taskScheduler.schedule(retryJob, Instant.now().plusMillis(delay));
    }

    private long calculateRetryDelay(int attempt, String retryPolicy) {
        if (!StringUtils.hasText(retryPolicy) || !retryPolicy.contains(":")) {
            return 30000; // Default to 30 seconds
        }

        String[] parts = retryPolicy.split(":", 2);
        String policyType = parts[0];
        long baseInterval = Long.parseLong(parts[1]);

        switch (policyType.toLowerCase()) {
            case "exponential":
                return baseInterval * (long) Math.pow(2, attempt - 1);
            case "fixed":
            default:
                return baseInterval;
        }
    }

    private TaskExecutionJob createTaskRunnable(TaskConfig taskConfig, ExecutionPattern executionPattern, Long parentLogId) {
        return new TaskExecutionJob(
                taskConfig,
                applicationContext,
                taskExecuteLogDao,
                distributedLockService,
                this,
                notificationService,
                distributedLockService.getSchedulerInstanceId(),
                1,
                executionPattern,
                parentLogId,
                taskConfig.getParameters(),
                executionPattern == ExecutionPattern.WORKFLOW_STEP ? taskConfig.getWorkflowNodeId() : null,
                null // A new log will be created, so no initial log id
        );
    }

    public boolean rescheduleTask(TaskConfig taskConfig) {
        logger.info("Rescheduling task {} ({}).", taskConfig.getTaskId(), taskConfig.getTaskName());
        boolean cancelled = cancelTask(taskConfig.getTaskId());
        if (cancelled) {
            scheduleTask(taskConfig);
            return true;
        } else {
            logger.warn("Could not reschedule task {} ({}): cancellation failed.", taskConfig.getTaskId(),
                    taskConfig.getTaskName());
            scheduleTask(taskConfig);
            return false;
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        logger.info("Application context refreshed. Scheduler initialization will be driven by periodic sync.");
        if (taskScheduler != null) {
            taskScheduler.execute(this::loadAndScheduleInitialTasks);
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info("Shutting down CoreSchedulerService. Cancelling all scheduled tasks.");
        if (this.taskScheduler != null) {
            this.taskScheduler.shutdown();
        }
        logger.info("All tasks cancelled and scheduler shut down.");
    }
}
