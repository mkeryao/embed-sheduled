package com.github.embed.scheduler.dto;

import java.sql.Timestamp;

import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionMode;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskExecuteLogDto {
    private Long logId;
    private Integer taskId;
    private Timestamp startTime;
    private Timestamp endTime;
    private ExecutionState state;
    private String rtnMsg;
    private String exMsg;
    private String instanceId;
    private Integer parentLogId;
    private ExecutionPattern taskPattern;
    private String workflowNodeId;
    private Integer taskType; // Added field from TaskConfig
    private String taskName; // Added field from TaskConfig
    private ExecutionMode executionMode;
    private Integer  attempt ;// Added field from TaskExecuteLog;

    public static TaskExecuteLogDto fromEntity(TaskExecuteLog log, Integer taskType, String taskName, ExecutionMode executionMode) {
        TaskExecuteLogDto dto = new TaskExecuteLogDto();
        dto.setLogId(log.getLogId());
        dto.setTaskId(log.getTaskId());
        dto.setStartTime(log.getStartTime());
        dto.setEndTime(log.getEndTime());
        dto.setState(log.getState());
        dto.setRtnMsg(log.getRtnMsg());
        dto.setExMsg(log.getExMsg());
        dto.setInstanceId(log.getInstanceId());
        dto.setParentLogId(log.getParentLogId());
        dto.setTaskPattern(log.getTaskPattern());
        dto.setWorkflowNodeId(log.getWorkflowNodeId());
        dto.setTaskType(taskType);
        dto.setTaskName(taskName);
        dto.setExecutionMode(executionMode);
        dto.setAttempt(log.getAttempt());
        return dto;
    }
}
