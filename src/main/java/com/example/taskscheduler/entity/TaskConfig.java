package com.example.taskscheduler.entity;

import java.sql.Timestamp; // Import the new enum

import com.example.taskscheduler.enums.ExecutionMode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the configuration for a schedulable task. This includes
 * cron expression, task type, execution parameters, advanced scheduling rules
 * (like exclusions, timeouts), notification settings, and workflow definitions
 * if the task is a workflow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfig {

    /**
     * Unique identifier for the task configuration.
     */
    private Integer taskId;

    /**
     * Name of the task, should be unique within its group.
     */
    private String taskName;

    /**
     * Group to which this task belongs, for organization.
     */
    private String taskGroup;

    /**
     * Cron expression defining when the task should run.
     */
    private String cronExpression;

    /**
     * Type of the task. 0: Bean task (executes a method on a Spring bean). 1:
     * (Legacy/Unused) 2: HTTP task (makes an HTTP request). 4: Shell script
     * task (executes a shell script). 10: Workflow task (orchestrates a series
     * of other tasks).
     */
    private int taskType;

    // --- Task Type Specific Parameters ---
    /**
     * Name of the Spring bean to invoke (for Bean tasks).
     */
    private String beanName;
    /**
     * Name of the method to invoke on the bean (for Bean tasks).
     */
    private String methodName;
    /**
     * JSON string representing parameters. Structure depends on taskType: -
     * Bean task (0): Parameters for the bean method. - HTTP task (2): JSON of
     * HttpTaskParameters (url, method, headers, body, timeouts). - Shell task
     * (4): JSON of ShellTaskParameters (script content/path, arguments, working
     * dir). - Workflow task (10): Not directly used by parent, but nodes have
     * their own parameters.
     */
    private String beanParameters;

    // HTTP and Shell specific fields below are deprecated in favor of storing structured JSON in beanParameters.
    // These fields are no longer mapped in DAO and will be removed in a future schema update.
    // private String httpUrl;
    // private String httpMethod;
    // private String httpHeaders;
    // private String httpBody;
    // private String scriptPath;
    // private String scriptParameters;
    // --- End Task Type Specific Parameters ---
    /**
     * Optional description of the task.
     */
    private String description;

    /**
     * Flag indicating if the task is active and should be scheduled.
     */
    private boolean isActive;

    // --- Execution Mode ---
    /**
     * Defines how the task behaves in a cluster: BROADCAST or CLUSTER (uses
     * lock).
     */
    private ExecutionMode executionMode;

    // --- Advanced Scheduling Features ---
    /**
     * Name of a {@link TaskCalendar#calendarName} to check for non-working
     * days. If the current day is a non-working day in this calendar, the task
     * will be skipped.
     */
    private String taskCalendarGroup;
    /**
     * Comma-separated time ranges during which the task should NOT run (e.g.,
     * "00:00-08:00,22:00-23:59"). Applies daily based on the scheduler's local
     * time.
     */
    private String taskExcludeTimes;
    /**
     * The task will not run before this date (inclusive).
     */
    private Timestamp startDate;
    /**
     * The task will not run after this date (inclusive).
     */
    private Timestamp endDate;
    /**
     * Maximum execution time in seconds for the task. 0 or null means no
     * timeout.
     */
    private Integer executeTimeoutSeconds;

    // --- Notification Settings ---
    /**
     * Comma-separated user IDs (from {@link TaskUser#userId}) to notify upon
     * successful task completion.
     */
    private String notifySuccessUserIds;
    /**
     * Comma-separated user IDs to notify upon failed or timed-out task
     * completion.
     */
    private String notifyFailedUserIds;

    // --- Workflow Definition (if taskType is WORKFLOW) ---
    /**
     * JSON string representing an array of
     * {@link com.example.taskscheduler.dto.workflow.WorkflowNode} objects.
     */
    private String workflowNodesJson;
    /**
     * JSON string representing an array of
     * {@link com.example.taskscheduler.dto.workflow.WorkflowEdge} objects.
     */
    private String workflowEdgesJson;
    /**
     * JSON string representing a map of global parameters for a workflow.
     */
    private String globalParametersJson;

    // --- Retry Configuration ---
    /**
     * Maximum number of retry attempts upon failure (0 means no retries).
     */
    private Integer maxRetryAttempts;
    /**
     * Interval in seconds between retry attempts.
     */
    private Integer retryIntervalSeconds;
    /**
     * Multiplier for retry interval (for exponential backoff). For example, 2.0
     * means each retry will wait twice as long as the previous one.
     */
    private Float retryIntervalMultiplier;

    // --- Timestamps ---
    /**
     * Timestamp of when this task configuration was created.
     */
    private Timestamp createTime;
    /**
     * Timestamp of the last update to this task configuration.
     */
    private Timestamp updateTime;
}
