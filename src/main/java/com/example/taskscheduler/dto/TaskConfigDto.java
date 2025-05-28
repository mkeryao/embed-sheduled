package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// This DTO can be used for request/response bodies for TaskConfig
// It's often good practice to use DTOs to decouple API contracts from entity structure,
// though for simple cases they might look very similar.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfigDto {
    private Integer taskId;
    private String taskName;
    private String taskGroup;
    private String cronExpression;
    private int taskType; // 0: Bean task, 1: HTTP task, 2: Shell script
    private String beanName;
    private String methodName;
    private String beanParameters; // JSON string for parameters
    private String httpUrl;
    private String httpMethod;
    private String httpHeaders; // JSON string for headers
    private String httpBody;
    private String scriptPath;
    private String scriptParameters;
    private String description;
    private boolean isActive;
    
    // Distributed lock fields
    private String taskLockName;
    private Integer taskLockMostSeconds;

    // Advanced features
    private String taskCalendarGroup;
    private String taskExcludeTimes;
    private java.sql.Date startDate;
    private java.sql.Date endDate;
    private Integer executeTimeoutSeconds;

    // Notification settings
    private String notifySuccessUserIds;
    private String notifyFailedUserIds;

    // Timestamps (usually for response)
    private java.sql.Timestamp createTime;
    private java.sql.Timestamp updateTime;

    // Workflow fields for DTO (parsed from/to JSON strings in Entity)
    private java.util.List<com.example.taskscheduler.dto.workflow.WorkflowNode> workflowNodes;
    private java.util.List<com.example.taskscheduler.dto.workflow.WorkflowEdge> workflowEdges;
    private java.util.Map<String, Object> globalParameters; // For workflow tasks
}
