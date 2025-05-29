package com.example.taskscheduler.dto;

import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for {@link com.example.taskscheduler.entity.TaskConfig}.
 * Used for transferring task configuration data between the client/API layer and the service layer.
 * It includes all fields from the entity, plus parsed representations of JSON-based workflow definitions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfigDto {
    private Integer taskId;
    private String taskName;
    private String taskGroup;
    private String cronExpression;

    /**
     * Type of the task.
     * 0: Bean task (executes a method on a Spring bean).
     * 1: Shell script (executes a shell script).
     * 2: HTTP task (makes an HTTP request).
     * 3: Workflow task (orchestrates a series of other tasks).
     */
    private int taskType;

    // Fields for BEAN task_type (taskType=0)
    private String beanName;
    private String methodName;
    /** 
     * Generic parameters field, structure depends on taskType:
     * - For BEAN (0): JSON string for bean method parameters.
     * - For SHELL (1): JSON string of ShellTaskParameters DTO.
     * - For HTTP (2): JSON string of HttpTaskParameters DTO.
     * - For WORKFLOW (3): Can be null or used for initial context if needed by a specific workflow interpretation.
     */
    private String beanParameters; 

    // Fields for HTTP task_type (taskType=2) are effectively covered by beanParameters storing HttpTaskParameters JSON.
    // These direct fields can be considered deprecated or for very simple HTTP tasks if not using HttpTaskParameters DTO.
    // For consistency, using beanParameters for complex types like HTTP and Shell is preferred.
    private String httpUrl; // Example: Can be part of HttpTaskParameters stored in beanParameters
    private String httpMethod;
    private String httpHeaders;
    private String httpBody;

    // Fields for SHELL task_type (taskType=1) are covered by beanParameters storing ShellTaskParameters JSON.
    // These direct fields can be considered deprecated.
    private String scriptPath; // Example: Can be part of ShellTaskParameters stored in beanParameters
    private String scriptParameters;

    private String description;
    private boolean isActive;
    
    // Distributed lock fields
    private String taskLockName;
    private Integer taskLockMostSeconds;

    // Advanced features
    /** Calendar group name (e.g., "NATIONAL_HOLIDAYS") to check for non-working days. */
    private String taskCalendarGroup;
    /** Comma-separated time ranges for daily exclusion, e.g., "00:00-08:00,22:00-23:59". */
    private String taskExcludeTimes;
    /** Task will not run before this date. */
    private java.sql.Date startDate;
    /** Task will not run after this date. */
    private java.sql.Date endDate;
    /** Execution timeout in seconds for the task. 0 means no timeout. */
    private Integer executeTimeoutSeconds;

    // Notification settings
    /** Comma-separated user IDs to notify on successful task completion. */
    private String notifySuccessUserIds;
    /** Comma-separated user IDs to notify on failed or timed-out task completion. */
    private String notifyFailedUserIds;

    // Timestamps (usually for response)
    private java.sql.Timestamp createTime;
    private java.sql.Timestamp updateTime;

    // Workflow fields for DTO (parsed from/to JSON strings in Entity)
    /** List of nodes defining the workflow structure. Only applicable if taskType is WORKFLOW. */
    private List<WorkflowNode> workflowNodes;
    /** List of edges defining transitions between workflow nodes. Only applicable if taskType is WORKFLOW. */
    private List<WorkflowEdge> workflowEdges;
    /** Global parameters for a workflow task, accessible within node parameter templating. Only applicable if taskType is WORKFLOW. */
    private Map<String, Object> globalParameters;
}
