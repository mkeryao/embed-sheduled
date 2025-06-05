package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceNodeStatusDto {
    private String nodeId;
    private String status; // e.g., SUCCESS, FAILED, RUNNING, SKIPPED, NOT_EXECUTED
    private Long lastLogId;
    private Timestamp lastStartTime;
    private Timestamp lastEndTime;
    private String lastMessage;
}
