package com.example.taskscheduler.scheduler;

import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.enums.ExecutionMode;
import com.example.taskscheduler.enums.ExecutionPattern;
import com.example.taskscheduler.service.BeanTaskExecutor; // Added
import com.example.taskscheduler.service.DistributedLockService;
import com.example.taskscheduler.service.HttpTaskExecutor;
import com.example.taskscheduler.service.NotificationService;
import com.example.taskscheduler.service.ShellTaskExecutor;
import com.example.taskscheduler.service.WorkflowExecutionService;

public class TaskExecutionJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutionJob.class);

    private final TaskConfig taskConfig;
    private final ApplicationContext applicationContext;
    private final TaskExecuteLogDao taskExecuteLogDao;
    private final DistributedLockService distributedLockService;
    private final CoreSchedulerService coreSchedulerService; // For callback
    private final NotificationService notificationService; // For notifications
    private final String instanceId;
    private final int attemptNumber;
    private final String initialTaskPattern; // Pattern of the first attempt
    private final Long parentLogId; // For WORKFLOW_STEP, this is the parent workflow's log ID
    private final String effectiveBeanParametersJson; // For overriding bean parameters for a single run
    private final String workflowNodeId; // Identifier of the node in the workflow, if applicable
    private Long executionLogId; // To store the log ID for this specific attempt

    public TaskExecutionJob(TaskConfig taskConfig,
            ApplicationContext applicationContext,
            TaskExecuteLogDao taskExecuteLogDao,
            DistributedLockService distributedLockService,
            CoreSchedulerService coreSchedulerService,
            NotificationService notificationService,
            String instanceId,
            int attemptNumber,
            String initialTaskPattern,
            Long parentLogId,
            String effectiveBeanParametersJson,
            String workflowNodeId) {
        this.taskConfig = taskConfig;
        this.applicationContext = applicationContext;
        this.taskExecuteLogDao = taskExecuteLogDao;
        this.distributedLockService = distributedLockService;
        this.coreSchedulerService = coreSchedulerService;
        this.notificationService = notificationService;
        this.instanceId = instanceId;
        this.attemptNumber = attemptNumber;
        this.initialTaskPattern = initialTaskPattern;
        this.parentLogId = parentLogId;
        this.effectiveBeanParametersJson = effectiveBeanParametersJson;
        this.workflowNodeId = workflowNodeId;
    }

    @Override
    public void run() {
        String finalStatus = "FAILED"; // Default to FAILED
        String exceptionMessage = null;
        String returnMessage = null;
        boolean lockAcquired = false;
        String derivedLockName = null;

        TaskExecuteLog log = new TaskExecuteLog();
        log.setTaskId(taskConfig.getTaskId());
        log.setStartTime(new Timestamp(System.currentTimeMillis()));
        log.setState("RUNNING");
        log.setInstanceId(this.instanceId);

        if (this.attemptNumber > 1) {
            log.setTaskPattern(ExecutionPattern.RETRY_ATTEMPT.name() + "_" + this.attemptNumber);
        } else {
            log.setTaskPattern(
                    this.initialTaskPattern != null ? this.initialTaskPattern : ExecutionPattern.NORMAL.name());
        }

        if (this.parentLogId != null) {
            log.setParentLogId(this.parentLogId.intValue()); // Corrected setter and added intValue() for Long to
                                                             // Integer conversion
        }

        // Set workflow_node_id if this is a workflow step
        if (ExecutionPattern.WORKFLOW_STEP.name().equals(log.getTaskPattern()) && this.workflowNodeId != null
                && !this.workflowNodeId.isEmpty()) {
            log.setWorkflowNodeId(this.workflowNodeId);
        }

        // If a DB column `attempt_number` was added to `task_execute_log`, set it here:
        // log.setAttemptNumber(this.attemptNumber);
        TaskExecuteLog savedLog = taskExecuteLogDao.save(log);
        this.executionLogId = savedLog.getLogId();
        MDC.put("execute_no", String.valueOf(this.executionLogId));
        MDC.put("task_id", String.valueOf(taskConfig.getTaskId()));
        MDC.put("task_name", taskConfig.getTaskName());
        MDC.put("attempt_no", String.valueOf(this.attemptNumber));

        logger.info("Attempt {} for task: {} (ID: {}, Log ID: {})",
                this.attemptNumber, taskConfig.getTaskName(), taskConfig.getTaskId(), this.executionLogId);

        try {
            // Distributed Lock Acquisition
            if (taskConfig.getExecutionMode() == ExecutionMode.CLUSTER) {
                derivedLockName = "task_lock_id_" + taskConfig.getTaskId();
                lockAcquired = distributedLockService.tryLock(derivedLockName, this.instanceId);
                if (!lockAcquired) {
                    finalStatus = "SKIPPED";
                    returnMessage = "Skipped: Could not acquire CLUSTER lock '" + derivedLockName + "'";
                    logger.warn("{} for task ID {}", returnMessage, taskConfig.getTaskId());
                    return; // Do not proceed with execution
                }
                logger.info("CLUSTER Lock '{}' acquired for task ID {}", derivedLockName, taskConfig.getTaskId());
            }

            // Exclusion checks (date, calendar, time) - These are now primarily handled by
            // CustomTaskTrigger for cron.
            // For manual triggers or direct retries, these checks might still be relevant
            // here if not done by caller.
            // For simplicity of retry logic, assuming these checks are bypassed for
            // retries, or handled if necessary.
            logger.info("Executing task: {} (ID: {}, Log ID: {}, Attempt: {})",
                    taskConfig.getTaskName(), taskConfig.getTaskId(), this.executionLogId, this.attemptNumber);

            // Actual Task Execution
            switch (taskConfig.getTaskType()) {
                case 0: // Bean task
                    BeanTaskExecutor beanTaskExecutor = applicationContext.getBean(BeanTaskExecutor.class);
                    TaskConfig configForBeanRun = taskConfig; // Default to original
                    if (this.effectiveBeanParametersJson != null && !this.effectiveBeanParametersJson.isEmpty()) {
                        // Create a temporary copy to modify beanParameters for this run only
                        configForBeanRun = new TaskConfig();
                        org.springframework.beans.BeanUtils.copyProperties(taskConfig, configForBeanRun);
                        configForBeanRun.setBeanParameters(this.effectiveBeanParametersJson);
                        logger.debug("Using effectiveBeanParameters for task ID {}, Log ID {}", taskConfig.getTaskId(),
                                this.executionLogId);
                    }
                    beanTaskExecutor.execute(configForBeanRun); // This might throw exceptions including timeout
                    finalStatus = "SUCCESS"; // If no exception
                    break;
                case 2: // HTTP task
                    HttpTaskExecutor httpTaskExecutor = applicationContext.getBean(HttpTaskExecutor.class);
                    // HttpTaskExecutor's execute method needs to be refactored to not update log
                    // status itself,
                    // but rather return status or throw exception. For now, assume it throws on
                    // failure.
                    httpTaskExecutor.execute(taskConfig, savedLog); // Pass savedLog for it to update if it must
                    // If httpTaskExecutor updates the log, we need to fetch its state.
                    TaskExecuteLog httpUpdatedLog = taskExecuteLogDao.findById(this.executionLogId).orElse(savedLog);
                    finalStatus = httpUpdatedLog.getState(); // Rely on executor's update
                    exceptionMessage = httpUpdatedLog.getExMsg();
                    returnMessage = httpUpdatedLog.getRtnMsg();
                    break;
                case 4: // Shell script task
                    ShellTaskExecutor shellTaskExecutor = applicationContext.getBean(ShellTaskExecutor.class);
                    shellTaskExecutor.execute(taskConfig, savedLog);
                    TaskExecuteLog shellUpdatedLog = taskExecuteLogDao.findById(this.executionLogId).orElse(savedLog);
                    finalStatus = shellUpdatedLog.getState();
                    exceptionMessage = shellUpdatedLog.getExMsg();
                    returnMessage = shellUpdatedLog.getRtnMsg();
                    break;
                case 10: // Workflow task
                    WorkflowExecutionService workflowService = applicationContext
                            .getBean(WorkflowExecutionService.class);
                    workflowService.startWorkflow(taskConfig, savedLog);
                    // Workflow status is complex; assume it sets its own final log status or calls
                    // back separately.
                    // For this basic retry, we might only retry if the initial startWorkflow itself
                    // fails.
                    // If startWorkflow is synchronous and throws error for immediate failure:
                    TaskExecuteLog wfUpdatedLog = taskExecuteLogDao.findById(this.executionLogId).orElse(savedLog);
                    finalStatus = wfUpdatedLog.getState();
                    exceptionMessage = wfUpdatedLog.getExMsg();
                    returnMessage = wfUpdatedLog.getRtnMsg();
                    break;
                default:
                    finalStatus = "FAILED";
                    exceptionMessage = "Unknown task type: " + taskConfig.getTaskType();
                    logger.error(exceptionMessage + " for task ID: {}", taskConfig.getTaskId());
                    break;
            }
            if ("RUNNING".equals(finalStatus)
                    && "SUCCESS".equals(taskExecuteLogDao.findById(this.executionLogId).orElse(savedLog).getState())) {
                // If executor updated to SUCCESS inside its method.
                finalStatus = "SUCCESS";
            }

        } catch (BeanTaskExecutor.TaskTimeoutException e) {
            logger.error("Task {} (ID: {}) timed out on attempt {}.", taskConfig.getTaskName(), taskConfig.getTaskId(),
                    this.attemptNumber, e);
            finalStatus = "TIMED_OUT";
            exceptionMessage = e.getMessage();
        } catch (Exception e) {
            logger.error("Task {} (ID: {}) failed on attempt {} with an unexpected exception.",
                    taskConfig.getTaskName(), taskConfig.getTaskId(), this.attemptNumber, e);
            finalStatus = "FAILED";
            exceptionMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (lockAcquired && derivedLockName != null) {
                distributedLockService.unlock(derivedLockName, this.instanceId);
                logger.info("CLUSTER Lock '{}' released for task ID {}", derivedLockName, taskConfig.getTaskId());
            }

            // Update final log status for this attempt
            // If an executor (HTTP/Shell/Workflow) already set a terminal state, respect
            // it.
            TaskExecuteLog currentLogState = taskExecuteLogDao.findById(this.executionLogId).orElse(null);
            if (currentLogState != null && "RUNNING".equals(currentLogState.getState())) {
                taskExecuteLogDao.updateLogStatus(this.executionLogId, finalStatus, returnMessage, exceptionMessage);
            } else if (currentLogState == null) { // Should not happen
                logger.error("Log entry {} disappeared before final update for task {}", this.executionLogId,
                        taskConfig.getTaskId());
            }
            // Fetch the final state again for notification and completion handler
            TaskExecuteLog finalLogEntry = taskExecuteLogDao.findById(this.executionLogId).orElse(savedLog); // Refresh
                                                                                                             // log
                                                                                                             // state

            // Send notification for this attempt's outcome
            notificationService.sendNotification(taskConfig, finalLogEntry);

            // Call completion handler for retry logic (or final success logging)
            // Pass the final status of *this attempt*, and context for potential retries.
            coreSchedulerService.handleTaskCompletion(taskConfig,
                    finalLogEntry.getState(),
                    this.attemptNumber,
                    this.executionLogId,
                    this.initialTaskPattern,
                    this.parentLogId,
                    this.workflowNodeId); // Pass workflowNodeId for context

            MDC.clear();
        }
    }
}
