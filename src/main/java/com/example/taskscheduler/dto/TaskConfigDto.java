package com.example.taskscheduler.dto;

import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.enums.ExecutionMode; // Import the new enum
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
     * Type of the task, determining its execution behavior.
     * <ul>
     *   <li>{@code 0}: Bean Task - Executes a method on a specified Spring bean.</li>
     *   <li>{@code 1}: (Legacy/Unused)</li>
     *   <li>{@code 2}: HTTP Task - Makes an HTTP request.</li>
     *   <li>{@code 4}: Shell Script Task - Executes a shell script.</li>
     *   <li>{@code 10}: Workflow Task - Orchestrates a series of other tasks.</li>
     * </ul>
     */
    private int taskType;

    // Fields for BEAN task_type (taskType=0)
    private String beanName;
    private String methodName;
    /**
     * Generic parameters field, structure depends on taskType:
     * <ul>
     *   <li>For BEAN (0): JSON string representing parameters for the bean method.</li>
     *   <li>For HTTP (2): JSON string representing {@link com.example.taskscheduler.dto.taskparams.HttpTaskParameters}.</li>
     *   <li>For SHELL (4): JSON string representing {@link com.example.taskscheduler.dto.taskparams.ShellTaskParameters}.</li>
     *   <li>For WORKFLOW (10): Typically null or empty for the parent workflow task itself, as parameters are usually defined within nodes or globally.</li>
     * </ul>
     */
    private String beanParameters;

    // Fields for HTTP task_type (taskType=2) are effectively covered by beanParameters storing HttpTaskParameters JSON.
    // These direct fields can be considered deprecated or for very simple HTTP tasks if not using HttpTaskParameters DTO.
    // For consistency, using beanParameters for complex types like HTTP and Shell is preferred.
    /** @deprecated Prefer storing HTTP URL within the JSON of {@code beanParameters} (as part of HttpTaskParameters). */
    @Deprecated
    private String httpUrl;
    /** @deprecated Prefer storing HTTP Method within the JSON of {@code beanParameters}. */
    @Deprecated
    private String httpMethod;
    /** @deprecated Prefer storing HTTP Headers within the JSON of {@code beanParameters}. */
    @Deprecated
    private String httpHeaders;
    /** @deprecated Prefer storing HTTP Body within the JSON of {@code beanParameters}. */
    @Deprecated
    private String httpBody;

    // Fields for SHELL task_type (taskType=4, formerly 1) are covered by beanParameters storing ShellTaskParameters JSON.
    // These direct fields can be considered deprecated.
    /** @deprecated Prefer storing script path within the JSON of {@code beanParameters} (as part of ShellTaskParameters). */
    @Deprecated
    private String scriptPath;
    /** @deprecated Prefer storing script arguments within the JSON of {@code beanParameters}. */
    @Deprecated
    private String scriptParameters;

    private String description;
    private boolean isActive;

    /**
     * Defines how the task behaves in a cluster. Defaults to {@link ExecutionMode#BROADCAST}.
     * <ul>
     *   <li>{@link ExecutionMode#BROADCAST}: Task runs on all instances.</li>
     *   <li>{@link ExecutionMode#CLUSTER}: Task attempts to acquire a distributed lock to run on a single instance.
     *       The lock name is automatically derived (e.g., `task_lock_id_{taskId}`).</li>
     * </ul>
     */
    private ExecutionMode executionMode = ExecutionMode.BROADCAST;

    // Advanced features
    /** Calendar group name (e.g., "NATIONAL_HOLIDAYS") to check for non-working days. */
    private String taskCalendarGroup;
    /** Comma-separated time ranges for daily exclusion, e.g., "00:00-08:00,22:00-23:59". */
    private String taskExcludeTimes;
    /** Task will not run before this date time. */
    private java.sql.Timestamp startDate;
    /** Task will not run after this date time. */
    private java.sql.Timestamp endDate;
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
