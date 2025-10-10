package com.github.embed.scheduler.scheduler;

import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;

import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionMode;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import com.github.embed.scheduler.service.BeanTaskExecutor;
import com.github.embed.scheduler.service.DistributedLockService;
import com.github.embed.scheduler.service.HttpTaskExecutor;
import com.github.embed.scheduler.service.NotificationService;
import com.github.embed.scheduler.service.ShellTaskExecutor;
import com.github.embed.scheduler.service.WorkflowExecutionService;

public class TaskExecutionJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionJob.class);

    private final TaskConfig taskConfig;
    private final ApplicationContext applicationContext;
    private final TaskExecuteLogDao taskExecuteLogDao;
    private final DistributedLockService distributedLockService;
    private final CoreSchedulerService coreSchedulerService;
    private final NotificationService notificationService;
    private final String instanceId;
    private final int attemptNumber;
    private final String executionPattern;
    private final Long parentLogId;
    private final String parameters;
    private final String workflowNodeId;
    private final Long initialExecutionLogId; // Can be null for new tasks

    private Long executionLogId; // The ID for this specific run

    public TaskExecutionJob(TaskConfig taskConfig,
                            ApplicationContext applicationContext,
                            TaskExecuteLogDao taskExecuteLogDao,
                            DistributedLockService distributedLockService,
                            CoreSchedulerService coreSchedulerService,
                            NotificationService notificationService,
                            String instanceId,
                            int attemptNumber,
                            String executionPattern,
                            Long parentLogId,
                            String parameters,
                            String workflowNodeId,
                            Long initialExecutionLogId) {
        this.taskConfig = taskConfig;
        this.applicationContext = applicationContext;
        this.taskExecuteLogDao = taskExecuteLogDao;
        this.distributedLockService = distributedLockService;
        this.coreSchedulerService = coreSchedulerService;
        this.notificationService = notificationService;
        this.instanceId = instanceId;
        this.attemptNumber = attemptNumber;
        this.executionPattern = executionPattern;
        this.parentLogId = parentLogId;
        this.parameters = parameters;
        this.workflowNodeId = workflowNodeId;
        this.initialExecutionLogId = initialExecutionLogId;
    }

    @Override
    public void run() {
        // If an initial log ID is provided (e.g., for a workflow node), use it.
        // Otherwise, create a new log entry.
        if (this.initialExecutionLogId != null) {
            this.executionLogId = this.initialExecutionLogId;
            // The state should have already been set to RUNNING by the caller
        } else {
            TaskExecuteLog log = new TaskExecuteLog();
            log.setTaskId(taskConfig.getTaskId());
            log.setStartTime(new Timestamp(System.currentTimeMillis()));
            log.setState(ExecutionState.RUNNING.name());
            log.setInstanceId(this.instanceId);
            log.setAttempt(this.attemptNumber);
            log.setTaskPattern(this.executionPattern);
            log.setParentLogId(this.parentLogId != null ? this.parentLogId.intValue() : null);
            log.setParameters(this.parameters);
            log.setWorkflowNodeId(this.workflowNodeId);
            
            TaskExecuteLog savedLog = taskExecuteLogDao.save(log);
            this.executionLogId = savedLog.getId();
        }

        MDC.put("execute_no", String.valueOf(this.executionLogId));
        MDC.put("task_id", String.valueOf(taskConfig.getTaskId()));
        MDC.put("task_name", taskConfig.getTaskName());
        MDC.put("attempt_no", String.valueOf(this.attemptNumber));

        logger.info("Attempt {} for task: {} (ID: {}, Log ID: {})",
                this.attemptNumber, taskConfig.getTaskName(), taskConfig.getTaskId(), this.executionLogId);
        
        String finalStatus = ExecutionState.FAILED.name();
        String returnMessage = null;
        String exceptionMessage = null;
        boolean lockAcquired = false;
        String derivedLockName = null;

        try {
            if (taskConfig.getExecutionMode() == ExecutionMode.CLUSTER) {
                derivedLockName = "task_lock_id_" + taskConfig.getTaskId();
                lockAcquired = distributedLockService.tryLock(derivedLockName, this.instanceId);
                if (!lockAcquired) {
                    finalStatus = ExecutionState.SKIPPED.name();
                    returnMessage = "Skipped: Could not acquire CLUSTER lock '" + derivedLockName + "'";
                    logger.warn("{} for task ID {}", returnMessage, taskConfig.getTaskId());
                    return;
                }
                logger.info("CLUSTER Lock '{}' acquired for task ID {}", derivedLockName, taskConfig.getTaskId());
            }

            switch (taskConfig.getTaskType()) {
                case 0: // Bean task
                    BeanTaskExecutor beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
                    // The taskConfig passed to this job's constructor already has the correct parameters
                    // (either from the node or the original task), so we can use it directly.
                    returnMessage = beanTaskExecutor.execute(taskConfig);
                    finalStatus = ExecutionState.SUCCESS.name();
                    break;
                case 10: // Workflow task
                    WorkflowExecutionService workflowService = applicationContext.getBean(WorkflowExecutionService.class);
                    workflowService.startWorkflow(taskConfig.getTaskId(), this.executionLogId);
                    finalStatus = ExecutionState.RUNNING.name(); // Workflow itself is now running
                    break;
                // Other task types (HTTP, Shell) would go here
                default:
                    exceptionMessage = "Unknown task type: " + taskConfig.getTaskType();
                    logger.error(exceptionMessage + " for task ID: {}", taskConfig.getTaskId());
                    finalStatus = ExecutionState.FAILED.name();
                    break;
            }

        } catch (BeanTaskExecutor.TaskTimeoutException e) {
            logger.error("Task {} (ID: {}) timed out on attempt {}.", taskConfig.getTaskName(), taskConfig.getTaskId(), this.attemptNumber, e);
            finalStatus = ExecutionState.TIMED_OUT.name();
            exceptionMessage = e.getMessage();
        } catch (Exception e) {
            logger.error("Task {} (ID: {}) failed on attempt {} with an unexpected exception.", taskConfig.getTaskName(), taskConfig.getTaskId(), this.attemptNumber, e);
            finalStatus = ExecutionState.FAILED.name();
            exceptionMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (lockAcquired && derivedLockName != null) {
                distributedLockService.unlock(derivedLockName, this.instanceId);
                logger.info("CLUSTER Lock '{}' released for task ID {}", derivedLockName, taskConfig.getTaskId());
            }

            // Don't update status for parent workflow logs, as their status is managed by the workflow service
            if (!ExecutionPattern.WORKFLOW_PARENT.name().equals(this.executionPattern)) {
                taskExecuteLogDao.updateLogStatus(this.executionLogId, finalStatus, returnMessage, exceptionMessage);
            }

            // Callback to the scheduler for completion handling (retries, workflow progression)
            if (!ExecutionState.RUNNING.name().equals(finalStatus)) {
                 coreSchedulerService.handleTaskCompletion(this.executionLogId);
            }

            MDC.clear();
        }
    }
}
