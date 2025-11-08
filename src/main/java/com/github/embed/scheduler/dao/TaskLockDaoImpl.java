package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of the {@link TaskLockDao} interface.
 * Handles database operations for {@link TaskLock} entities using Spring's {@link JdbcTemplate}.
 * Provides mechanisms for acquiring, releasing, and managing distributed locks.
 */
@Repository
public class TaskLockDaoImpl implements TaskLockDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskLockDaoImpl.class);

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    // --- SQL 语句 (MySQL 方言) ---
    // 插入新锁，版本从 1 开始
    private static final String INSERT_LOCK_SQL =
            "INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, least_duration_seconds, version) " +
                    "VALUES (?, ?, NOW(), ?, 1)";

    // 尝试更新一个已过期的锁。
    // 安全关键点 1: 检查租约是否过期
    private static final String UPDATE_EXPIRED_LOCK_SQL =
            "UPDATE task_lock SET " +
                    "  owner_instance_id = ?, " +
                    "  lock_acquired_time = NOW(), " +
                    "  least_duration_seconds = ?, " +
                    "  version = version + 1 " + // 递增版本
                    "WHERE lock_name = ? AND (lock_acquired_time + INTERVAL (least_duration_seconds ) SECOND < NOW())";

    // "更新-后-确认" 查询：获取锁的当前所有者和版本
    // 安全关键点 2: 更新后必须立刻确认所有权并获取新版本
    private static final String SELECT_OWNER_VERSION_SQL =
            "SELECT owner_instance_id, version , lock_name ,least_duration_seconds , lock_acquired_time FROM task_lock WHERE lock_name = ? ";

    // 续约锁
    // 安全关键点 3: 必须同时匹配 owner 和 version (CAS)
    private static final String RENEW_LOCK_SQL =
            "UPDATE task_lock SET " +
                    "  lock_acquired_time = NOW(), " +
                    "  least_duration_seconds = ?, " +
                    "  version = ? " +                 // 设置新版本
                    "WHERE lock_name = ? AND owner_instance_id = ? AND version = ?"; // 严格的 CAS 检查

    // 释放锁
    // 安全关键点 4: 必须同时匹配 owner 和 version (CAS)
    private static final String DELETE_LOCK_SQL =
            "DELETE FROM task_lock " +
                    "WHERE lock_name = ? AND owner_instance_id = ? AND version = ?";

    private static final String UPDATE_LOCK_GENERAL_SQL = "UPDATE task_lock SET owner_instance_id=?, lock_acquired_time=?, least_duration_seconds=?, version=? WHERE lock_name=?";


    private final RowMapper<TaskLock> rowMapper = (rs, rowNum) -> {
        TaskLock lock = new TaskLock();
        lock.setLockName(rs.getString("lock_name"));
        lock.setOwnerInstanceId(rs.getString("owner_instance_id"));
        lock.setLockAcquiredTime(rs.getTimestamp("lock_acquired_time"));
        lock.setLeastDurationSeconds(rs.getObject("least_duration_seconds", Integer.class));
        lock.setVersion(rs.getObject("version", Integer.class));
        return lock;
    };

    @Override
    public void save(TaskLock lock) {
        try {
            jdbcTemplate.update(INSERT_LOCK_SQL, lock.getLockName(),
                    lock.getOwnerInstanceId(), lock.getLockAcquiredTime(),
                    lock.getLeastDurationSeconds(), lock.getVersion());
        } catch (DuplicateKeyException e) {
            logger.warn("Lock with name {} already exists during save attempt. Consider ensureLockRecordExists or update.", lock.getLockName());
        }
    }

    @Override
    public int update(TaskLock lock) {
        return jdbcTemplate.update(UPDATE_LOCK_GENERAL_SQL,
                lock.getOwnerInstanceId(),
                lock.getLockAcquiredTime(),
                lock.getLeastDurationSeconds(),
                lock.getVersion(),
                lock.getLockName());
    }



    @Override
    public Optional<TaskLock> findByLockName(String lockName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_OWNER_VERSION_SQL, new Object[]{lockName}, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(transactionManager = "schedulerTransactionManager" , isolation = Isolation.READ_COMMITTED)
    public Optional<TaskLock> tryAcquireOrRefreshLock(String lockName,
                                                      String ownerInstanceId,
                                                      int leastDurationSeconds) {
        // 1. 尝试插入 (最高性能的路径)
        try {
            jdbcTemplate.update(INSERT_LOCK_SQL, lockName, ownerInstanceId, leastDurationSeconds);
            // 插入成功，我们获得了锁，版本为 1
            return Optional.of(new TaskLock(lockName, ownerInstanceId, 1));
        } catch ( DataIntegrityViolationException e) {
            // 主键冲突，锁已存在。进入步骤 2。
            logger.warn("Lock [{}] already exists, cannot insert. Trying to update expired lock.", lockName);
        }catch (Exception e) {
            // 其他数据库异常
            // log.error("Error while trying to insert lock", e);
            return Optional.empty();
        }

        // 2. 尝试更新一个已过期的锁
        try {
            int updatedRows = jdbcTemplate.update(UPDATE_EXPIRED_LOCK_SQL,
                    ownerInstanceId, leastDurationSeconds, lockName
            );

            if (updatedRows == 0) {
                // 锁存在，但未过期（或在我们更新前被别人更新了）
                return Optional.empty();
            }

            // 3. 更新成功 (updatedRows == 1)。我们 *认为* 我们获取了锁。
            // 必须进行“更新-后-确认”，以获取新版本号并防止竞态条件。
            try {
                Optional<TaskLock>  taskLockOpt = findByLockName(lockName)  ;
                if(!taskLockOpt.isPresent()) {
                    return taskLockOpt;
                }
                String currentOwner = taskLockOpt.get().getOwnerInstanceId() ;
                int currentVersion = taskLockOpt.get().getVersion() ;

                if (ownerInstanceId.equals(currentOwner)) {
                    return Optional.of(new TaskLock(lockName, ownerInstanceId, currentVersion));
                } else {
                    // 我们更新了锁，但在我们查询之前，另一个进程又更新（偷走）了它。
                    // 我们获取锁失败。
                    return Optional.empty();
                }

            } catch (EmptyResultDataAccessException ex) {
                // 锁在我们更新后、查询前被删除了。非常罕见，但意味着我们失败了。
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error while trying to update expired lock", e);
            return Optional.empty();
        }
    }


    @Override
    @Transactional(transactionManager = "schedulerTransactionManager")
    public boolean releaseLock(TaskLock taskLock) {
        if(Objects.isNull(taskLock)){
            return false;
        }
        int rowsAffected = jdbcTemplate.update(DELETE_LOCK_SQL, taskLock.getLockName() ,
                taskLock.getOwnerInstanceId() ,
                taskLock.getVersion() // 严格的 CAS 检查
                );
        if (rowsAffected > 0) {
            logger.debug("Lock [{}] released by instance [{}]", taskLock.getLockName(),
                    taskLock.getOwnerInstanceId());
            return true;
        } else {
            // This can happen if the lock expired and was claimed by another, or was never owned by this instance.
            logger.warn("Failed to release lock [{}] by instance [{}]. Lock not found, not owned by this instance, or already released/expired.",
                    taskLock.getLockName(), taskLock.getOwnerInstanceId());
            return false;
        }
    }
}
