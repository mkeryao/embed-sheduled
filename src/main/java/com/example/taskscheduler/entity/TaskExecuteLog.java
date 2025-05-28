package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecuteLog {
    private Integer logId;
    private int taskId;
    private Timestamp startTime;
    private Timestamp endTime;
    private String state; // RUNNING, SUCCESS, FAILED, TIMEOUT, SKIPPED
    private String exMsg; // Exception message if any
    private String instanceId;
    private Integer parentLogId; // Corresponds to schema's parent_execute_no, referencing log_id of parent
    private String taskPattern; // e.g., NORMAL, WORKFLOW_STEP
}
