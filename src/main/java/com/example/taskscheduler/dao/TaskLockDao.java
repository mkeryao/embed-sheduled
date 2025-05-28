package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskLock;
import java.util.Optional;

public interface TaskLockDao {
    Optional<TaskLock> findByLockName(String lockName);
    
    /**
     * Attempts to acquire a lock.
     * This can be by inserting a new record if one doesn't exist for the lockName,
     * or by updating an existing one if it's expired or owned by the same ownerInstanceId.
     *
     * @param lockName The name of the lock.
     * @param ownerInstanceId The ID of the instance trying to acquire the lock.
     * @param leaseDurationMs The duration for which the lock should be held, in milliseconds.
     *                        A value of 0 or less might signify an indefinite lock, handled by a very large lease duration.
     * @return true if the lock was successfully acquired or refreshed, false otherwise.
     */
    boolean tryAcquireOrRefreshLock(String lockName, String ownerInstanceId, int leaseDurationMs);

    /**
     * Releases a lock if it is currently held by the specified ownerInstanceId.
     *
     * @param lockName The name of the lock.
     * @param ownerInstanceId The ID of the instance that owns the lock.
     * @return true if the lock was successfully released, false otherwise (e.g., lock not found or not owned by this instance).
     */
    boolean releaseLock(String lockName, String ownerInstanceId);

    // To initially create a lock record if it might not exist, as per schema.sql example data
    void ensureLockRecordExists(String lockName);

    // Keep save and update for potential admin operations, but they are not the primary lock mechanism
    void save(TaskLock lock);
    int update(TaskLock lock);
}
