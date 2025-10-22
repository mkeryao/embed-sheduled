package com.github.embed.scheduler.service;

import com.github.embed.scheduler.dao.TaskLockDao;
import com.github.embed.scheduler.entity.TaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Service for managing distributed locks using a database table (`task_lock`).
 * It provides mechanisms to acquire and release locks, with retry logic for acquisition.
 * The scheduler instance ID is used to identify lock owners.
 */
@Service
public class DistributedLockService implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);


    @Autowired
    private TaskLockDao taskLockDao;


    /**
     * Maximum number of attempts to acquire a lock.
     */
    @Value("${scheduler.lock.retry.maxAttempts:1}")
    private int maxLockAttempts;

    /**
     * Delay in milliseconds between lock acquisition retries.
     */
    @Value("${scheduler.lock.retry.delayMs:1000}")
    private long lockRetryDelayMs;


    @Value("${scheduler.lock.least.second:30}")
    private int lockLeastSecond = 30; // 60 seconds

    @Value("${scheduler.instance.id:#{null}}")
    private String schedulerInstanceId;

    /**
     * Initializes the service, setting up the scheduler instance ID.
     * It also ensures that a placeholder record for a global scheduler lock exists in the database.
     */

    public void init() throws UnknownHostException {
        if (!StringUtils.hasText(schedulerInstanceId)) {
            //获取当前机器的IP和Name
            this.schedulerInstanceId = java.net.InetAddress.getLocalHost().getHostName()
                    + ":" + java.net.InetAddress.getLocalHost().getHostAddress() ;
                   // + ":" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            logger.info("scheduler.instance.id not configured, instance id: {}", schedulerInstanceId);
        }
        logger.info("DistributedLockService initialized. Max lock attempts: {}, Retry delay: {}ms", maxLockAttempts, lockRetryDelayMs);
        // Ensure a common lock record exists if needed, e.g., for a global leader election lock
        taskLockDao.tryAcquireOrRefreshLock("GLOBAL_SCHEDULER_LOCK" , schedulerInstanceId , 20 );
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
    public  Optional<TaskLock> tryLock(String lockName, String owner) {
        if ( !StringUtils.hasText(lockName) ||  !StringUtils.hasText(owner) ) {
            logger.warn("Lock name or owner is null/empty. LockName: '{}', Owner: '{}'", lockName, owner);
            return Optional.empty();
        }

        logger.info("Attempting to acquire lock [{}] for owner [{}]. Lease: {}Seconds." +
                        " Max attempts: {}. Retry delay: {}ms.",
                lockName, owner, lockLeastSecond, maxLockAttempts, lockRetryDelayMs);

        for (int attempt = 1; attempt <= maxLockAttempts; attempt ++) {
            logger.debug("Lock acquisition attempt {}/{} for lock [{}] by owner [{}].", attempt, maxLockAttempts, lockName, owner);
            Optional<TaskLock> acquiredLock = taskLockDao.tryAcquireOrRefreshLock(lockName, owner, lockLeastSecond);

            if (acquiredLock.isPresent()) {
                logger.info("Lock [{}] successfully acquired by owner [{}] on attempt {}.", lockName, owner, attempt);
                return acquiredLock;
            }

            logger.warn("Lock acquisition attempt {}/{} failed for lock [{}] by owner [{}].", attempt, maxLockAttempts, lockName, owner);

            if (attempt < maxLockAttempts) {
                try {
                    logger.info("Waiting {}ms before next lock acquisition attempt for lock [{}].", lockRetryDelayMs, lockName);
                    Thread.sleep(lockRetryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Lock acquisition retry sleep interrupted for lock [{}]. Failing early.", lockName, e);
                    return Optional.empty();
                }
            }
        }

        logger.warn("Failed to acquire lock [{}] for owner [{}] after {} attempts.", lockName, owner, maxLockAttempts);
        return Optional.empty();
    }

    /**
     * Releases a distributed lock if it is currently held by the specified owner.
     *
     * @param taskLock The name of the lock to release.
     */
    public void unlock(TaskLock taskLock) {
        if (!StringUtils.hasText(taskLock.getLockName())) {
            logger.warn("Attempted to unlock with null or empty lockName.");
            return;
        }
         if (!StringUtils.hasText(taskLock.getOwnerInstanceId())) {
            logger.warn("Attempted to unlock with null or empty owner for lockName: {}", taskLock.getLockName());
            return;
        }

        boolean released = taskLockDao.releaseLock(taskLock);
        if (released) {
            logger.info("Lock [{}] successfully released by owner [{}].", taskLock.getLockName(), taskLock.getOwnerInstanceId());
        } else {
            logger.warn("Failed to release lock [{}] by owner [{}]. It might not have been owned by this instance or was already released/expired.", taskLock.getLockName(), taskLock.getOwnerInstanceId());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }
}
