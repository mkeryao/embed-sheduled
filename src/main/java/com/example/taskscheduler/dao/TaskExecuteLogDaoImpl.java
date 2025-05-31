package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskExecuteLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of the {@link TaskExecuteLogDao} interface.
 * Handles database operations for {@link TaskExecuteLog} entities
 * using Spring's {@link JdbcTemplate}.
 */
@Repository
public class TaskExecuteLogDaoImpl implements TaskExecuteLogDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecuteLogDaoImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String LOG_COLUMNS = "log_id, task_id, start_time, end_time, state, rtn_msg, ex_msg, instance_id, parent_execute_no, task_pattern";
    private static final String INSERT_SQL = "INSERT INTO task_execute_log (task_id, start_time, state, instance_id, parent_execute_no, task_pattern, rtn_msg, ex_msg) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE task_execute_log SET task_id=?, start_time=?, end_time=?, state=?, rtn_msg=?, ex_msg=?, instance_id=?, parent_execute_no=?, task_pattern=? WHERE log_id=?";
    private static final String SELECT_BY_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE log_id=?";
    private static final String SELECT_ALL_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log ORDER BY start_time DESC";
    private static final String SELECT_BY_TASK_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE task_id=? ORDER BY start_time DESC";
    private static final String UPDATE_LOG_STATUS_SQL = "UPDATE task_execute_log SET end_time=CURRENT_TIMESTAMP, state=?, rtn_msg=?, ex_msg=? WHERE log_id=?";


    private final RowMapper<TaskExecuteLog> rowMapper = (rs, rowNum) -> {
        TaskExecuteLog log = new TaskExecuteLog();
        log.setLogId(rs.getInt("log_id"));
        log.setTaskId(rs.getInt("task_id"));
        log.setStartTime(rs.getTimestamp("start_time"));
        log.setEndTime(rs.getTimestamp("end_time"));
        log.setState(rs.getString("state"));
        log.setRtnMsg(rs.getString("rtn_msg"));
        log.setExMsg(rs.getString("ex_msg"));
        log.setInstanceId(rs.getString("instance_id"));
        log.setParentLogId(rs.getObject("parent_execute_no", Integer.class));
        log.setTaskPattern(rs.getString("task_pattern"));
        return log;
    };

    @Override
    public TaskExecuteLog save(TaskExecuteLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, log.getTaskId());
            ps.setTimestamp(2, log.getStartTime() != null ? log.getStartTime() : new Timestamp(System.currentTimeMillis()));
            ps.setString(3, log.getState());
            ps.setString(4, log.getInstanceId());
            if (log.getParentLogId() != null) {
                ps.setInt(5, log.getParentLogId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, log.getTaskPattern());
            ps.setString(7, log.getRtnMsg());
            ps.setString(8, log.getExMsg());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            log.setLogId(keyHolder.getKey().intValue());
        }
        return log;
    }

    @Override
    public Optional<TaskExecuteLog> findById(Integer logId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, new Object[]{logId}, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskExecuteLog> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    @Override
    public List<TaskExecuteLog> findByTaskId(Integer taskId) {
        return jdbcTemplate.query(SELECT_BY_TASK_ID_SQL, new Object[]{taskId}, rowMapper);
    }

    @Override
    public int update(TaskExecuteLog log) {
        return jdbcTemplate.update(UPDATE_SQL,
                log.getTaskId(), log.getStartTime(), log.getEndTime(),
                log.getState(), log.getRtnMsg(), log.getExMsg(), log.getInstanceId(),
                log.getParentLogId(), log.getTaskPattern(),
                log.getLogId());
    }

    @Override
    public void updateLogStatus(Integer logId, String state, String exMsg) {
        String rtnMsgForUpdate = exMsg;
        if ("SUCCESS".equals(state)) {
           rtnMsgForUpdate = "Task completed successfully.";
        }
        // Basic length capping to avoid DB errors
        if (rtnMsgForUpdate != null && rtnMsgForUpdate.length() > 1950) {
            rtnMsgForUpdate = rtnMsgForUpdate.substring(0, 1950) + "...";
        }
        if (exMsg != null && exMsg.length() > 1950) {
            exMsg = exMsg.substring(0, 1950) + "...";
        }
        jdbcTemplate.update(UPDATE_LOG_STATUS_SQL, state, rtnMsgForUpdate, exMsg, logId);
    }

    @Override
    public List<Map<String, Object>> getOverallStatusCounts() {
        String sql = "SELECT state, COUNT(*) as count FROM task_execute_log GROUP BY state";
        return jdbcTemplate.queryForList(sql);
    }

    @Override
    public List<Map<String, Object>> getTaskStatusCounts(Integer taskId) {
        String sql = "SELECT state, COUNT(*) as count FROM task_execute_log WHERE task_id = ? GROUP BY state";
        return jdbcTemplate.queryForList(sql, taskId);
    }

    @Override
    public List<Map<String, Object>> getTopNExecutedTasks(int n) {
        // Note: task_name is in task_config table. This query needs a JOIN.
        String sql = "SELECT tel.task_id, tc.task_name, COUNT(tel.log_id) as execution_count " +
                     "FROM task_execute_log tel " +
                     "JOIN task_config tc ON tel.task_id = tc.task_id " +
                     "GROUP BY tel.task_id, tc.task_name " +
                     "ORDER BY execution_count DESC " +
                     "LIMIT ?";
        return jdbcTemplate.queryForList(sql, n);
    }

    @Override
    public Double getAverageExecutionTime(Integer taskId) {
        // Calculate time difference in seconds for MySQL: TIMESTAMPDIFF(SECOND, start_time, end_time)
        // For H2 (and standard SQL): (JULIANDAY(end_time) - JULIANDAY(start_time)) * 86400.0 for seconds
        // Or more simply, if end_time and start_time are Epoch millis: (end_time_millis - start_time_millis) / 1000.0
        // Assuming timestamps are convertible to epoch millis for simplicity or using DB specific functions:
        // For H2/PostgreSQL: EXTRACT(EPOCH FROM (end_time - start_time)) * 1000
        // For MySQL: UNIX_TIMESTAMP(end_time) * 1000 - UNIX_TIMESTAMP(start_time) * 1000
        // The current schema uses TIMESTAMP, which can be tricky.
        // A simpler approach for average of (end_time - start_time) if the DB supports direct subtraction resulting in an interval
        // For now, let's assume a DB function or cast that gives milliseconds or seconds.
        // Using AVG on direct subtraction might work on some DBs or need casting.
        // This is a placeholder for a more robust cross-db solution or db-specific queries.
        // Let's try with a common approach that might work for H2/Postgres.
        // For MySQL, it would be AVG(TIMESTAMPDIFF(MILLISECOND, start_time, end_time))
        String sql = "SELECT AVG(CAST(TIMESTAMPDIFF(MILLISECOND, start_time, end_time) AS DOUBLE)) " +
                     "FROM task_execute_log " +
                     "WHERE task_id = ? AND state = 'SUCCESS' AND start_time IS NOT NULL AND end_time IS NOT NULL";
        try {
            // queryForObject for AVG can return null if no rows match
            return jdbcTemplate.queryForObject(sql, new Object[]{taskId}, Double.class);
        } catch (EmptyResultDataAccessException e) {
            return null; // No successful runs or no runs at all
        } catch (Exception e) {
            logger.error("Error calculating average execution time for task ID {}: {}. SQL: {}", taskId, e.getMessage(), sql, e);
            // Fallback or try alternative SQL for different DB dialect if possible
            // For now, assume H2/MySQL-like TIMESTAMPDIFF is available or adapted by JDBC driver/DB.
            // A truly portable solution often requires dialect-specific queries or careful casting.
            // Trying a more generic approach for H2 (may not work on MySQL without function):
             String h2Sql = "SELECT AVG(DATEDIFF('MILLISECOND', start_time, end_time)) " +
                           "FROM task_execute_log " +
                           "WHERE task_id = ? AND state = 'SUCCESS' AND start_time IS NOT NULL AND end_time IS NOT NULL";
            try {
                 return jdbcTemplate.queryForObject(h2Sql, new Object[]{taskId}, Double.class);
            } catch (Exception e2) {
                 logger.error("Error calculating average execution time for task ID {} with H2 SQL: {}. SQL: {}", taskId, e2.getMessage(), h2Sql, e2);
                 return null;
            }
        }
    }

    @Override
    public Long getTotalRuns(Integer taskId) {
        String sql = "SELECT COUNT(*) FROM task_execute_log WHERE task_id = ?";
        try {
            Long count = jdbcTemplate.queryForObject(sql, new Object[]{taskId}, Long.class);
            return count != null ? count : 0L;
        } catch (EmptyResultDataAccessException e) {
            // COUNT(*) should not throw this, it always returns a row.
            return 0L;
        } catch (Exception e) {
            logger.error("Error getting total runs for task ID {}: {}", taskId, e.getMessage(), e);
            return 0L;
        }
    }
}
