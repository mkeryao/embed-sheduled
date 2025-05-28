package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskLockDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.UUID;

@Service
public class DistributedLockService {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);

    @Autowired
    private TaskLockDao taskLockDao;

    @Value("${scheduler.instance.id:#{null}}")
    private String configuredInstanceId;

    @Value("${scheduler.lock.retry.maxAttempts:3}")
    private int maxLockAttempts;

    @Value("${scheduler.lock.retry.delayMs:1000}")
    private long lockRetryDelayMs;


    private String schedulerInstanceId;

    @PostConstruct
    public void init() {
        if (configuredInstanceId == null || configuredInstanceId.trim().isEmpty()) {
            schedulerInstanceId = UUID.randomUUID().toString();
            logger.info("scheduler.instance.id not configured, generated UUID: {}", schedulerInstanceId);
        } else {
            schedulerInstanceId = configuredInstanceId;
            logger.info("scheduler.instance.id configured as: {}", schedulerInstanceId);
        }
        logger.info("DistributedLockService initialized. Max lock attempts: {}, Retry delay: {}ms", maxLockAttempts, lockRetryDelayMs);
        taskLockDao.ensureLockRecordExists("GLOBAL_SCHEDULER_LOCK");
    }

    public String getSchedulerInstanceId() {
        return schedulerInstanceId;
    }

    public boolean tryLock(String lockName, String owner, int lockMostSeconds) {
        if (lockName == null || lockName.trim().isEmpty() || owner == null || owner.trim().isEmpty()) {
            logger.warn("Lock name or owner is null/empty. LockName: '{}', Owner: '{}'", lockName, owner);
            return false;
        }

        int leaseDurationMs = (lockMostSeconds <= 0) ? Integer.MAX_VALUE : lockMostSeconds * 1000;
        if (leaseDurationMs == Integer.MAX_VALUE) {
             logger.debug("Lock [{}] requested by owner [{}] for indefinite duration.", lockName, owner);
        }


        logger.info("Attempting to acquire lock [{}] for owner [{}]. Lease: {}ms. Max attempts: {}. Retry delay: {}ms.",
                lockName, owner, leaseDurationMs, maxLockAttempts, lockRetryDelayMs);

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
                    // Consider exponential backoff here if needed: delayMs *= 2;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Lock acquisition retry sleep interrupted for lock [{}]. Failing early.", lockName, e);
                    return false; // Exit if interrupted
                }
            }
        }

        logger.warn("Failed to acquire lock [{}] for owner [{}] after {} attempts.", lockName, owner, maxLockAttempts);
        return false;
    }

    /**
     * Releases a distributed lock.
     *
     * @param lockName The name of the lock.
     * @param owner The identifier of the entity that currently holds the lock.
     */
    public void unlock(String lockName, String owner) {
        if (lockName == null || lockName.trim().isEmpty()) {
            logger.warn("Attempted to unlock with null or empty lockName.");
            return; // Or throw IllegalArgumentException
        }
         if (owner == null || owner.trim().isEmpty()) {
            logger.warn("Attempted to unlock with null or empty owner for lockName: {}", lockName);
            return; // Or throw IllegalArgumentException
        }

        boolean released = taskLockDao.releaseLock(lockName, owner);
        if (released) {
            logger.info("Lock [{}] successfully released by owner [{}].", lockName, owner);
        } else {
            logger.warn("Failed to release lock [{}] by owner [{}]. It might not have been owned by this instance or was already released.", lockName, owner);
        }
    }
}
