package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskLock;
import java.util.Optional;

/**
 * Data Access Object interface for {@link TaskLock} entities.
 * Defines methods for acquiring, releasing, and querying distributed locks.
 * These locks are used to ensure that certain tasks or operations are performed
 * by only one instance in a distributed environment.
 */
public interface TaskLockDao {
    /**
     * Finds a lock by its name.
     * @param lockName The unique name of the lock.
     * @return An {@link Optional} containing the lock if found, or empty otherwise.
     */
    Optional<TaskLock> findByLockName(String lockName);

    /**
     * Attempts to acquire or refresh an existing lock.
     * This method tries to update the lock record in the database, setting the current instance as the owner
     * and updating the lock acquisition time and lease duration. It typically involves an atomic operation
     * or a transaction to ensure that only one instance can acquire the lock at a time.
     * The lock can be acquired if:
     * <ul>
     *   <li>It is not currently owned.</li>
     *   <li>It is owned by the same {@code ownerInstanceId} (effectively refreshing/extending the lock).</li>
     *   <li>It is expired (current time is past {@code lock_acquired_time + lease_duration_ms}).</li>
     * </ul>
     *
     * @param lockName The name of the lock.
     * @param ownerInstanceId The ID of the instance trying to acquire/refresh the lock.
     * @param leastDurationSeconds The duration in milliseconds for which the lock should be held or extended.
     * @return {@code true} if the lock was successfully acquired or refreshed, {@code false} otherwise.
     */
    Optional<TaskLock> tryAcquireOrRefreshLock(String lockName, String ownerInstanceId, int leastDurationSeconds);

    /**
     * Releases a lock if it is currently held by the specified owner instance.
     * This typically involves setting the owner instance ID and lock acquisition time to null.
     *
     * @param taskLock The name of the lock.
     * @return {@code true} if the lock was successfully released, {@code false} otherwise
     *         (e.g., lock not found, or not owned by this instance).
     */
    boolean releaseLock(TaskLock taskLock);



    /**
     * Saves a new lock record. This is generally used for initial setup or administrative purposes,
     * not for typical lock acquisition logic (which uses {@code tryAcquireOrRefreshLock}).
     * @param lock The {@link TaskLock} entity to save.
     */
    void save(TaskLock lock);

    /**
     * Updates an existing lock record. This is a general update method, potentially for administrative use.
     * Typical lock state changes are handled by {@code tryAcquireOrRefreshLock} and {@code releaseLock}.
     * @param lock The {@link TaskLock} entity with updated values.
     * @return The number of rows affected.
     */
    int update(TaskLock lock);
}
