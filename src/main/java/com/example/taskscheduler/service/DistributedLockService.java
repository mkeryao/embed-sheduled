package com.example.taskscheduler.service;

import java.net.UnknownHostException;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.taskscheduler.dao.TaskLockDao;

/**
 * Service for managing distributed locks using a database table (`task_lock`).
 * It provides mechanisms to acquire and release locks, with retry logic for acquisition.
 * The scheduler instance ID is used to identify lock owners.
 */
@Service
public class DistributedLockService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);
    private static final int DEFAULT_CLUSTER_LOCK_LEASE_SECONDS = 10; // 60 seconds
    private static final int DEFAULT_CLUSTER_LOCK_LEASE_MS = DEFAULT_CLUSTER_LOCK_LEASE_SECONDS * 1000; // Convert to ms for internal use

    @Autowired
    private TaskLockDao taskLockDao;

    /**
     * Configurable scheduler instance ID. If not provided, a UUID is generated.
     * Used to identify the owner of a lock.
     */
    @Value("${scheduler.instance.id:#{null}}")
    private String configuredInstanceId;

    /**
     * Maximum number of attempts to acquire a lock.
     */
    @Value("${scheduler.lock.retry.maxAttempts:3}")
    private int maxLockAttempts;

    /**
     * Delay in milliseconds between lock acquisition retries.
     */
    @Value("${scheduler.lock.retry.delayMs:1000}")
    private long lockRetryDelayMs;


    private String schedulerInstanceId;

    /**
     * Initializes the service, setting up the scheduler instance ID.
     * It also ensures that a placeholder record for a global scheduler lock exists in the database.
     */
    @PostConstruct
    public void init() throws UnknownHostException {
        if (!StringUtils.hasText(schedulerInstanceId)) {
            //获取当前机器的IP和Name
            this.schedulerInstanceId = java.net.InetAddress.getLocalHost().getHostName()
                    + ":" + java.net.InetAddress.getLocalHost().getHostAddress()
                    + ":" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            logger.info("scheduler.instance.id not configured, instance id: {}", schedulerInstanceId);
        } else {
            this.schedulerInstanceId = configuredInstanceId;
            logger.info("scheduler.instance.id configured as: {}", schedulerInstanceId);
        }
        logger.info("DistributedLockService initialized. Max lock attempts: {}, Retry delay: {}ms", maxLockAttempts, lockRetryDelayMs);
        // Ensure a common lock record exists if needed, e.g., for a global leader election lock
        taskLockDao.ensureLockRecordExists("GLOBAL_SCHEDULER_LOCK");
    }

    /**
     * Retrieves the unique identifier for this scheduler instance.
     * @return The scheduler instance ID.
     */
    public String getSchedulerInstanceId() {
        return schedulerInstanceId;
    }

    /**
     * Attempts to acquire or refresh a distributed lock with retry logic.
     * The lock lease duration is determined by the internal constant {@code DEFAULT_CLUSTER_LOCK_LEASE_MS}.
     *
     * @param lockName The name of the lock.
     * @param owner The identifier of the entity attempting to acquire the lock (typically the scheduler instance ID).
     * @return {@code true} if the lock was successfully acquired or refreshed, {@code false} otherwise.
     */
    public boolean tryLock(String lockName, String owner) {        if ( !StringUtils.hasText(lockName) ||  !StringUtils.hasText(owner) ) {
            logger.warn("Lock name or owner is null/empty. LockName: '{}', Owner: '{}'", lockName, owner);
            return false;
        }
        
        int leaseDurationMs = DEFAULT_CLUSTER_LOCK_LEASE_MS;        
        logger.info("Attempting to acquire lock [{}] for owner [{}]. Lease: {} seconds. Max attempts: {}. Retry delay: {}ms.",
                lockName, owner, DEFAULT_CLUSTER_LOCK_LEASE_SECONDS, maxLockAttempts, lockRetryDelayMs);

        for (int attempt = 1; attempt <= maxLockAttempts; attempt++) {
            logger.debug("Lock acquisition attempt {}/{} for lock [{}] by owner [{}].", attempt, maxLockAttempts, lockName, owner);
            boolean acquired = taskLockDao.tryAcquireOrRefreshLock(lockName, owner, leaseDurationMs);

            if (acquired) {
                logger.info("Lock [{}] successfully acquired by owner [{}] on attempt {}.", lockName, owner, attempt);
                return true;
            }

            logger.warn("Lock acquisition attempt {}/{} failed for lock [{}] by owner [{}].", attempt, maxLockAttempts, lockName, owner);

            if (attempt < maxLockAttempts) {
                try {
                    logger.info("Waiting {}ms before next lock acquisition attempt for lock [{}].", lockRetryDelayMs, lockName);
                    Thread.sleep(lockRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Lock acquisition retry sleep interrupted for lock [{}]. Failing early.", lockName, e);
                    return false;
                }
            }
        }

        logger.warn("Failed to acquire lock [{}] for owner [{}] after {} attempts.", lockName, owner, maxLockAttempts);
        return false;
    }

    /**
     * Releases a distributed lock if it is currently held by the specified owner.
     *
     * @param lockName The name of the lock to release.
     * @param owner The identifier of the entity that currently holds the lock.
     */
    public void unlock(String lockName, String owner) {
        if (!StringUtils.hasText(lockName)) {
            logger.warn("Attempted to unlock with null or empty lockName.");
            return;
        }
         if (!StringUtils.hasText(owner)) {
            logger.warn("Attempted to unlock with null or empty owner for lockName: {}", lockName);
            return;
        }

        boolean released = taskLockDao.releaseLock(lockName, owner);
        if (released) {
            logger.info("Lock [{}] successfully released by owner [{}].", lockName, owner);
        } else {
            logger.warn("Failed to release lock [{}] by owner [{}]. It might not have been owned by this instance or was already released/expired.", lockName, owner);
        }
    }
}
