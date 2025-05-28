package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class TaskLockDaoImpl implements TaskLockDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskLockDaoImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String SELECT_BY_LOCK_NAME_SQL = "SELECT lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version FROM task_lock WHERE lock_name = ?";
    
    // For ensureLockRecordExists - insert if not exists
    private static final String ENSURE_LOCK_SQL_H2 = "MERGE INTO task_lock (lock_name, version) KEY(lock_name) VALUES (?, 0)";
    // MySQL equivalent would be INSERT IGNORE or INSERT ... ON DUPLICATE KEY UPDATE
    // For simplicity, schema.sql pre-populates a dummy lock. Production might need more robust handling.
    // Let's assume H2 for now for this MERGE statement. If MySQL, this needs adjustment or a different strategy.
    // Given the schema.sql has an INSERT for GLOBAL_SCHEDULER_LOCK, we can assume locks are pre-defined for now.


    private static final String UPDATE_LOCK_OWNER_SQL =
        "UPDATE task_lock SET owner_instance_id = ?, lock_acquired_time = CURRENT_TIMESTAMP, lease_duration_ms = ?, version = version + 1 " +
        "WHERE lock_name = ?";

    private static final String INSERT_LOCK_OWNER_SQL =
        "INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version) VALUES (?, ?, CURRENT_TIMESTAMP, ?, 1)";

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
            logger.warn("Lock with name {} already exists. Use update or ensureLockRecordExists.", lock.getLockName());
            // Optionally, convert to update logic here or let caller handle
        }
    }

    @Override
    public int update(TaskLock lock) {
        return jdbcTemplate.update(UPDATE_LOCK_GENERAL_SQL, lock.getOwnerInstanceId(), lock.getLockAcquiredTime(), lock.getLeaseDurationMs(), lock.getVersion(), lock.getLockName());
    }
    
    @Override
    public void ensureLockRecordExists(String lockName) {
        // This is primarily for H2's MERGE or similar upsert.
        // The schema.sql already has an example lock. If a lock is used that's not in schema.sql,
        // this method would be called to create a placeholder.
        // For a generic solution, one might try SELECT then INSERT if not found.
        Optional<TaskLock> existing = findByLockName(lockName);
        if (!existing.isPresent()) {
            try {
                 // Insert a base, unlocked record.
                jdbcTemplate.update("INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version) VALUES (?, NULL, NULL, NULL, 0)", lockName);
                logger.info("Placeholder lock record created for {}", lockName);
            } catch (DuplicateKeyException e) {
                logger.warn("Concurrent attempt to create placeholder lock for {}. It likely exists now.", lockName);
            } catch (DataAccessException e) {
                logger.error("Error creating placeholder lock record for {}: {}", lockName, e.getMessage());
            }
        }
    }


    @Override
    public Optional<TaskLock> findByLockName(String lockName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_LOCK_NAME_SQL, new Object[]{lockName}, rowMapper));
        } catch (DataAccessException e) { // Specifically EmptyResultDataAccessException
            return Optional.empty();
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE) // Higher isolation for proper lock management
    public boolean tryAcquireOrRefreshLock(String lockName, String ownerInstanceId, int leaseDurationMsEffective) {
        // Ensure a base record exists to avoid issues with SELECT FOR UPDATE if the row isn't there.
        // This is a simplified way; in real systems, the lock record should ideally always pre-exist.
        ensureLockRecordExists(lockName); // Create a placeholder if it doesn't exist

        // Re-fetch with FOR UPDATE if supported and necessary, or rely on SERIALIZABLE isolation.
        // For simplicity, SERIALIZABLE is used. With lower isolation, SELECT ... FOR UPDATE would be needed.
        TaskLock existingLock = jdbcTemplate.queryForObject(SELECT_BY_LOCK_NAME_SQL + " FOR UPDATE", new Object[]{lockName}, rowMapper);
        // If queryForObject returns null (should not happen if ensureLockRecordExists works), handle it.
        // However, queryForObject throws EmptyResultDataAccessException if no row found, which ensureLockRecordExists tries to prevent.

        if (existingLock == null) { // Should ideally not be reached if ensureLockRecordExists is effective
            logger.warn("Lock record for {} was unexpectedly null after ensuring existence.", lockName);
            return false; // Or attempt insert again, though SERIALIZABLE should prevent concurrent delete.
        }

        Timestamp currentTime = Timestamp.from(Instant.now());
        boolean isExpired = true; // Assume expired if no valid lock time or lease
        if (existingLock.getOwnerInstanceId() != null && existingLock.getLockAcquiredTime() != null && existingLock.getLeaseDurationMs() != null) {
            Timestamp lockExpiryTime = Timestamp.from(existingLock.getLockAcquiredTime().toInstant().plusMillis(existingLock.getLeaseDurationMs()));
            isExpired = currentTime.after(lockExpiryTime);
        }
        
        if (existingLock.getOwnerInstanceId() == null || isExpired || ownerInstanceId.equals(existingLock.getOwnerInstanceId())) {
            // Lock is available (not owned, expired, or owned by current instance)
            int rowsAffected = jdbcTemplate.update(UPDATE_LOCK_OWNER_SQL,
                    ownerInstanceId, leaseDurationMsEffective, lockName);
            if (rowsAffected > 0) {
                logger.debug("Lock [{}] acquired/refreshed by instance [{}]. Lease: {} ms", lockName, ownerInstanceId, leaseDurationMsEffective);
                return true;
            } else {
                // This case should ideally not be hit if the SELECT...FOR UPDATE and transaction work correctly,
                // as it implies another transaction modified the lock after we selected it.
                logger.warn("Failed to update lock [{}] for instance [{}], possibly due to concurrent modification despite transaction.", lockName, ownerInstanceId);
                return false;
            }
        } else {
            // Lock is held by another active instance
            logger.debug("Lock [{}] currently held by instance [{}] until approx. {}. Cannot be acquired by [{}].",
                    lockName, existingLock.getOwnerInstanceId(),
                    existingLock.getLockAcquiredTime() != null && existingLock.getLeaseDurationMs() != null ?
                            Timestamp.from(existingLock.getLockAcquiredTime().toInstant().plusMillis(existingLock.getLeaseDurationMs())) : "N/A",
                    ownerInstanceId);
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
            // Could be that the lock didn't exist, or was not owned by this instance, or expired and was claimed by another.
            logger.warn("Failed to release lock [{}] by instance [{}]. Lock not found, not owned, or already released/expired.", lockName, ownerInstanceId);
            return false;
        }
    }
}
