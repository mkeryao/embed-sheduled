package com.github.embed.scheduler.scheduler;

import java.sql.Timestamp;
import java.util.Optional;

import com.github.embed.scheduler.entity.TaskLock;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
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

@Data
public class TaskExecutionJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionJob.class);

    private  TaskConfig taskConfig;
    private  ApplicationContext applicationContext;
    private  TaskExecuteLogDao taskExecuteLogDao;
    private  DistributedLockService distributedLockService;
    private  CoreSchedulerService coreSchedulerService;
    private  NotificationService notificationService;
    private  String instanceId;
    private  Integer attemptNumber;
    private  ExecutionPattern executionPattern;
    private  Long parentLogId;
    private  String parameters;
    private  String workflowNodeId;
    private  Long executionLogId; // The ID for this specific run

    public TaskExecutionJob(TaskConfig taskConfig,
                            ApplicationContext applicationContext,
                            TaskExecuteLogDao taskExecuteLogDao,
                            DistributedLockService distributedLockService,
                            CoreSchedulerService coreSchedulerService,
                            NotificationService notificationService,
                            String instanceId,
                            int attemptNumber,
                            ExecutionPattern executionPattern,
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
        this.executionLogId = initialExecutionLogId;
    }

    @Override
    public void run() {


        MDC.put("uuid",  this.executionLogId + ":" + taskConfig.getTaskId());
        logger.info("Execute {} Attempt {} for task: {} (ID: {}, Log ID: {})",
                executionPattern.name(),   this.attemptNumber, taskConfig.getTaskName(), taskConfig.getTaskId(), this.executionLogId);
        
        ExecutionState finalStatus = ExecutionState.FAILED;
        String returnMessage = null;
        String exceptionMessage = null;
        Optional<TaskLock> lockAcquired = Optional.empty();
        String derivedLockName = null;

        try {
            if (taskConfig.getExecutionMode() == ExecutionMode.CLUSTER) {
                derivedLockName = "task_lock_id_" + taskConfig.getTaskId();
                lockAcquired = distributedLockService.tryLock(derivedLockName, this.instanceId);
                if (!lockAcquired.isPresent()) {
                    finalStatus = ExecutionState.SKIPPED;
                    returnMessage = "Skipped: Could not acquire CLUSTER lock '" + derivedLockName + "'";
                    logger.warn("{} for task ID {}", returnMessage, taskConfig.getTaskId());
                    return;
                }
                logger.info("CLUSTER Lock '{}' acquired for task ID {}", derivedLockName, taskConfig.getTaskId());
            }
            if(this.executionLogId == null){
                    TaskExecuteLog log = new TaskExecuteLog();
                    log.setTaskId(taskConfig.getTaskId());
                    log.setStartTime(new Timestamp(System.currentTimeMillis())); //
                    log.setState(ExecutionState.RUNNING);
                    log.setInstanceId(this.instanceId);
                    log.setAttemptNumber(this.attemptNumber);
                    log.setTaskPattern(this.executionPattern);
                    log.setParentLogId(this.parentLogId != null ? this.parentLogId.intValue() : null);
                    log.setParameters(this.parameters);
                    log.setWorkflowNodeId(this.workflowNodeId);
                    finalStatus = log.getState() ;
                    taskExecuteLogDao.save(log);
                    this.executionLogId = log.getId();
                    MDC.put("uuid",  this.executionLogId + "&" + taskConfig.getTaskId());
            }

            switch (taskConfig.getTaskType()) {
                case 0: // Bean task
                    BeanTaskExecutor beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
                    returnMessage = beanTaskExecutor.execute(taskConfig, this.executionLogId);
                    break;
                case 1: // Shell task
                    ShellTaskExecutor shellTaskExecutor = applicationContext.getBean(ShellTaskExecutor.class);
                    returnMessage = shellTaskExecutor.execute(taskConfig, this.executionLogId);
                    break;
                case 2: // Http task
                    HttpTaskExecutor httpTaskExecutor = applicationContext.getBean(HttpTaskExecutor.class);
                    returnMessage = httpTaskExecutor.execute(taskConfig, this.executionLogId);
                    break;
                case 10: // Workflow task
                    WorkflowExecutionService workflowService = applicationContext.getBean(WorkflowExecutionService.class);
                    workflowService.startWorkflow(taskConfig.getTaskId(), this.executionLogId);
                    break;
                default:
                    String errorMsg = "Unknown task type: " + taskConfig.getTaskType();
                    logger.error(errorMsg + " for task ID: {}", taskConfig.getTaskId());
                    TaskExecuteLog logEntry = taskExecuteLogDao.findById(this.executionLogId).orElse(null);
                    if(logEntry != null){
                        logEntry.setState(ExecutionState.FAILED);
                        logEntry.setExMsg(errorMsg);
                        taskExecuteLogDao.update(logEntry);
                    }
                    break;
            }

            // The individual executors are now responsible for creating and updating their own log records.
            // We just need to retrieve the final status for retry logic.
            TaskExecuteLog finalLogState = taskExecuteLogDao.findById(this.executionLogId).orElse(null);
            if (finalLogState != null) {
                finalStatus = finalLogState.getState();
                // returnMessage is now set from the executor's return value
                if (returnMessage == null) {
                    returnMessage = finalLogState.getRtnMsg();
                }
                exceptionMessage = finalLogState.getExMsg();
            } else if (taskConfig.getTaskType() != 10) { // Workflow parent logs are handled differently
                logger.warn("Could not find log entry for log ID {} after execution. Status may be incorrect.", this.executionLogId);
                finalStatus = ExecutionState.UNKNOWN; // A state to indicate we lost track
                exceptionMessage = "Log entry disappeared after execution.";
            }



        } catch (BeanTaskExecutor.TaskTimeoutException e) {
            logger.error("Task {} (ID: {}) timed out on attempt {}.", taskConfig.getTaskName(), taskConfig.getTaskId(), this.attemptNumber, e);
            finalStatus = ExecutionState.TIMED_OUT;
            exceptionMessage = e.getMessage();
        } catch (Exception e) {
            logger.error("Task {} (ID: {}) failed on attempt {} with an unexpected exception.", taskConfig.getTaskName(), taskConfig.getTaskId(), this.attemptNumber, e);
            finalStatus = ExecutionState.FAILED;
            exceptionMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (StringUtils.isNotBlank(derivedLockName) && lockAcquired.isPresent()) {
                distributedLockService.unlock(lockAcquired.get());
                logger.info("CLUSTER Lock '{}' released for task ID {}", derivedLockName, taskConfig.getTaskId());
            }

            // Don't update status for parent workflow logs, as their status is managed by the workflow service
            if (this.executionPattern != ExecutionPattern.WORKFLOW_PARENT) {
                taskExecuteLogDao.updateLogStatus(this.executionLogId, finalStatus, returnMessage, exceptionMessage);
            }

            // Callback to the scheduler for completion handling (retries, workflow progression)
            if (finalStatus != ExecutionState.RUNNING
                     && finalStatus != ExecutionState.SKIPPED) {
                //logger.info("[handleTaskCompletion({})]" , this.executionLogId);
                coreSchedulerService.handleTaskCompletion(this.executionLogId);
            }

            MDC.clear();
        }
    }
}
