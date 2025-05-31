package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC implementation of the {@link TaskLockDao} interface.
 * Handles database operations for {@link TaskLock} entities using Spring's {@link JdbcTemplate}.
 * Provides mechanisms for acquiring, releasing, and managing distributed locks.
 */
@Repository
public class TaskLockDaoImpl implements TaskLockDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskLockDaoImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SELECT_BY_LOCK_NAME_SQL = "SELECT lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version FROM task_lock WHERE lock_name = ?";

    // SQL to update an existing lock row if it's available (unowned, expired, or owned by the current instance).
    // The version increment helps in optimistic concurrency if needed elsewhere, or simply tracks changes.
    // Note: TIMESTAMPADD syntax is H2 specific. For MySQL, it would be DATE_ADD with INTERVAL.
    // This implementation assumes H2 or a compatible database for this specific function.
    // For broader compatibility, this part might need to be database-specific or use JPA/Hibernate which abstracts this.
    private static final String ACQUIRE_OR_REFRESH_LOCK_SQL =
        "UPDATE task_lock SET owner_instance_id = ?, lock_acquired_time = CURRENT_TIMESTAMP, lease_duration_ms = ?, version = version + 1 " +
        "WHERE lock_name = ? AND " +
        "(owner_instance_id IS NULL OR owner_instance_id = ? OR (lock_acquired_time IS NOT NULL AND lease_duration_ms IS NOT NULL AND CURRENT_TIMESTAMP > TIMESTAMPADD(MILLISECOND, lease_duration_ms, lock_acquired_time)))";

    private static final String RELEASE_LOCK_SQL =
        "UPDATE task_lock SET owner_instance_id = NULL, lock_acquired_time = NULL, lease_duration_ms = NULL, version = version + 1 " +
        "WHERE lock_name = ? AND owner_instance_id = ?";

    private static final String SAVE_LOCK_SQL = "INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_LOCK_GENERAL_SQL = "UPDATE task_lock SET owner_instance_id=?, lock_acquired_time=?, lease_duration_ms=?, version=? WHERE lock_name=?";


    private final RowMapper<TaskLock> rowMapper = (rs, rowNum) -> {
        TaskLock lock = new TaskLock();
        lock.setLockName(rs.getString("lock_name"));
        lock.setOwnerInstanceId(rs.getString("owner_instance_id"));
        lock.setLockAcquiredTime(rs.getTimestamp("lock_acquired_time"));
        lock.setLeaseDurationMs(rs.getObject("lease_duration_ms", Integer.class));
        lock.setVersion(rs.getObject("version", Integer.class));
        return lock;
    };

    @Override
    public void save(TaskLock lock) {
        try {
            jdbcTemplate.update(SAVE_LOCK_SQL, lock.getLockName(), lock.getOwnerInstanceId(), lock.getLockAcquiredTime(), lock.getLeaseDurationMs(), lock.getVersion());
        } catch (DuplicateKeyException e) {
            logger.warn("Lock with name {} already exists during save attempt. Consider ensureLockRecordExists or update.", lock.getLockName());
        }
    }

    @Override
    public int update(TaskLock lock) {
        return jdbcTemplate.update(UPDATE_LOCK_GENERAL_SQL, lock.getOwnerInstanceId(), lock.getLockAcquiredTime(), lock.getLeaseDurationMs(), lock.getVersion(), lock.getLockName());
    }

    @Override
    public void ensureLockRecordExists(String lockName) {
        Optional<TaskLock> existing = findByLockName(lockName);
        if (!existing.isPresent()) {
            try {
                // Insert a base, unlocked record with version 0.
                jdbcTemplate.update("INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version) VALUES (?, NULL, NULL, NULL, 0)", lockName);
                logger.info("Placeholder lock record created for '{}'", lockName);
            } catch (DuplicateKeyException e) {
                logger.warn("Concurrent attempt to create placeholder lock for '{}'. It likely exists now.", lockName);
            } catch (DataAccessException e) {
                logger.error("Error creating placeholder lock record for '{}': {}", lockName, e.getMessage());
            }
        }
    }


    @Override
    public Optional<TaskLock> findByLockName(String lockName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_LOCK_NAME_SQL, new Object[]{lockName}, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean tryAcquireOrRefreshLock(String lockName, String ownerInstanceId, int leaseDurationMsEffective) {
        ensureLockRecordExists(lockName);

        // The ACQUIRE_OR_REFRESH_LOCK_SQL attempts to update the lock atomically based on conditions.
        // It checks for null owner, same owner, or expired lock.
        // This is preferred over SELECT...FOR UPDATE then UPDATE for some DBs or simpler scenarios,
        // though SELECT...FOR UPDATE is more explicit row-locking.
        // SERIALIZABLE isolation helps ensure consistency.
        int rowsAffected = jdbcTemplate.update(ACQUIRE_OR_REFRESH_LOCK_SQL,
                ownerInstanceId, leaseDurationMsEffective,
                lockName,
                ownerInstanceId // For the owner_instance_id = ? part of the OR condition
        );

        if (rowsAffected > 0) {
            logger.debug("Lock [{}] acquired/refreshed by instance [{}]. Lease: {} ms", lockName, ownerInstanceId, leaseDurationMsEffective);
            return true;
        } else {
            // If no rows affected, it means the lock is actively held by another instance and is not expired.
            TaskLock currentLock = findByLockName(lockName).orElse(null);
            if (currentLock != null && currentLock.getOwnerInstanceId() != null && !ownerInstanceId.equals(currentLock.getOwnerInstanceId())) {
                 Timestamp lockExpiryTime = null;
                 if (currentLock.getLockAcquiredTime() != null && currentLock.getLeaseDurationMs() != null) {
                    lockExpiryTime = Timestamp.from(currentLock.getLockAcquiredTime().toInstant().plusMillis(currentLock.getLeaseDurationMs()));
                 }
                 // Only log as warning if it's confirmed held by *another* instance
                 if (lockExpiryTime != null && Instant.now().isBefore(lockExpiryTime.toInstant())) {
                    logger.warn("Failed to acquire lock [{}] for instance [{}]. Currently held by instance [{}] until approx. {}.",
                            lockName, ownerInstanceId, currentLock.getOwnerInstanceId(), lockExpiryTime);
                 } else {
                     logger.info("Lock [{}] for instance [{}] could not be acquired. It might be held by another instance or conditions not met. Current owner: [{}], Expiry: [{}]",
                            lockName, ownerInstanceId, currentLock.getOwnerInstanceId(), lockExpiryTime);
                 }
            } else {
                logger.warn("Failed to acquire lock [{}] for instance [{}]. Lock might be in an unexpected state or was just modified.", lockName, ownerInstanceId);
            }
            return false;
        }
    }


    @Override
    @Transactional
    public boolean releaseLock(String lockName, String ownerInstanceId) {
        int rowsAffected = jdbcTemplate.update(RELEASE_LOCK_SQL, lockName, ownerInstanceId);
        if (rowsAffected > 0) {
            logger.debug("Lock [{}] released by instance [{}]", lockName, ownerInstanceId);
            return true;
        } else {
            // This can happen if the lock expired and was claimed by another, or was never owned by this instance.
            logger.warn("Failed to release lock [{}] by instance [{}]. Lock not found, not owned by this instance, or already released/expired.", lockName, ownerInstanceId);
            return false;
        }
    }
}
