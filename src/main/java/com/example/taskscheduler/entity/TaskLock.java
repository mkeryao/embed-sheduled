package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

/**
 * Entity representing a distributed lock.
 * These locks are stored in the `task_lock` table and are used to ensure that
 * critical operations or tasks are executed by only one instance in a distributed environment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskLock {
    /**
     * The unique name of the lock (e.g., "GLOBAL_SCHEDULER_LOCK", "TASK_ID_123_LOCK").
     * This is the primary key.
     */
    private String lockName;

    /**
     * The identifier of the scheduler instance that currently holds this lock.
     * Null if the lock is not currently held.
     */
    private String ownerInstanceId;

    /**
     * Timestamp of when the lock was last acquired or refreshed.
     * Used in conjunction with {@link #leaseDurationMs} to determine if a lock has expired.
     */
    private Timestamp lockAcquiredTime;

    /**
     * The duration in milliseconds for which the lock is valid after being acquired/refreshed.
     * After this duration, the lock is considered expired and can be acquired by another instance.
     */
    private Integer leaseDurationMs;

    /**
     * A version number used for optimistic locking if needed, or simply as a counter
     * for lock updates. Can help in preventing stale lock operations.
     */
    private Integer version;
}
