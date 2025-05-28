package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskLock {
    private String lockName;
    private String ownerInstanceId;
    private Timestamp lockAcquiredTime;
    private Integer leaseDurationMs;
    private Integer version;
}
