package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskConfig {
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
    private Timestamp createTime;
    private Timestamp updateTime;

    // Fields for distributed lock
    private String taskLockName;
    private Integer taskLockMostSeconds; // Duration in seconds, null if not locked

    // Advanced features
    private String taskCalendarGroup; // Name of the calendar group (TaskCalendar.calendarName)
    private String taskExcludeTimes;  // Comma-separated time ranges, e.g., "00:00-08:00,22:00-23:59"
    private java.sql.Date startDate;  // Task will not run before this date
    private java.sql.Date endDate;    // Task will not run after this date
    private Integer executeTimeoutSeconds; // 0 means no timeout

    // Notification settings
    private String notifySuccessUserIds; // Comma-separated user IDs
    private String notifyFailedUserIds;  // Comma-separated user IDs

    // Workflow fields (stored as JSON strings in DB)
    private String workflowNodesJson; // JSON array of WorkflowNode
    private String workflowEdgesJson; // JSON array of WorkflowEdge
    private String globalParametersJson; // JSON map for global workflow parameters
}
