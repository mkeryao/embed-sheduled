package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

/**
 * Entity representing a log entry for a specific execution of a task.
 * It records the start time, end time, status, and other details of each execution.
 * For workflow tasks, this can also represent logs for individual steps within the workflow,
 * linked to a parent workflow log entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecuteLog {
    /** Unique identifier for this log entry. */
    private Long logId;

    /** ID of the {@link TaskConfig} this log entry pertains to. */
    private Integer taskId;

    /** Timestamp of when the task execution started. */
    private Timestamp startTime;

    /** Timestamp of when the task execution ended. Null if still running or did not complete. */
    private Timestamp endTime;

    /**
     * Current state of the task execution.
     * Examples: "RUNNING", "SUCCESS", "FAILED", "TIMED_OUT", "SKIPPED".
     */
    private String state;

    /** Optional return message or short summary of execution, distinct from exception messages. */
    private String rtnMsg;

    /** Optional message, typically used to store exception messages if the task failed or timed out. */
    private String exMsg;

    /** Identifier of the scheduler instance that executed this task. Useful in distributed environments. */
    private String instanceId;

    /**
     * If this log entry is for a step within a workflow, this field stores the {@link #logId}
     * of the parent workflow's main log entry. Null for regular tasks or main workflow logs.
     * Corresponds to the `parent_execute_no` column in the database.
     */
    private Integer parentLogId;

    /**
     * Describes the pattern of execution, e.g., "NORMAL" for regular tasks,
     * "WORKFLOW_PARENT" for the main log of a workflow, or "WORKFLOW_STEP" for individual steps in a workflow.
     */
    private String taskPattern;

    /**
     * If this log entry is for a step within a workflow (i.e., taskPattern is "WORKFLOW_STEP"),
     * this field stores the specific node ID from the workflow definition that this execution corresponds to.
     * Null for other types of tasks or if not applicable.
     */
    private String workflowNodeId;
}
