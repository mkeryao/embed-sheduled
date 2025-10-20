package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
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

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private static final String LOG_COLUMNS = "log_id, task_id, workflow_id, start_time, end_time, state, rtn_msg, ex_msg, workflow_instance_id, parent_log_id, task_pattern, workflow_node_id, parameters, instance_id,attempt_number";
    private static final String INSERT_SQL = "INSERT INTO task_execute_log (task_id, workflow_id, start_time, state, workflow_instance_id, parent_log_id, task_pattern, rtn_msg, ex_msg, workflow_node_id, parameters, instance_id,attempt_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";
    private static final String UPDATE_SQL = "UPDATE task_execute_log SET task_id=?, workflow_id=?, start_time=?, end_time=?, state=?, rtn_msg=?, ex_msg=?, workflow_instance_id=?, parent_log_id=?, task_pattern=?, workflow_node_id=?, parameters=?, instance_id=? WHERE log_id=?";
    private static final String SELECT_BY_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE log_id=?";
    private static final String SELECT_ALL_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log ORDER BY start_time DESC";
    private static final String SELECT_BY_TASK_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE task_id=? ORDER BY start_time DESC";
    private static final String UPDATE_LOG_STATUS_SQL = "UPDATE task_execute_log SET end_time=CURRENT_TIMESTAMP, state=?, rtn_msg=?, ex_msg=? WHERE log_id=?";


    private final RowMapper<TaskExecuteLog> rowMapper = (rs, rowNum) -> {
        TaskExecuteLog log = new TaskExecuteLog();
        log.setLogId(rs.getLong("log_id"));
        log.setTaskId(rs.getInt("task_id"));
        log.setWorkflowId(rs.getObject("workflow_id", Integer.class));
        log.setStartTime(rs.getTimestamp("start_time"));
        log.setEndTime(rs.getTimestamp("end_time"));
        log.setState(ExecutionState.valueOf(rs.getString("state")));
        log.setRtnMsg(rs.getString("rtn_msg"));
        log.setExMsg(rs.getString("ex_msg"));
        log.setParameters(rs.getString("parameters"));
        log.setInstanceId(rs.getString("instance_id"));

        log.setWorkflowInstanceId(rs.getObject("workflow_instance_id", Long.class));

        log.setParentLogId(rs.getObject("parent_log_id", Integer.class));
        log.setTaskPattern(ExecutionPattern.valueOf(rs.getString("task_pattern")));
        log.setWorkflowNodeId(rs.getString("workflow_node_id"));
        log.setAttemptNumber(rs.getObject("attempt_number", Integer.class));
        return log;
    };

    @Override
    public TaskExecuteLog save(TaskExecuteLog log) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"log_id"});
            ps.setInt(1, log.getTaskId());
            ps.setObject(2, log.getWorkflowId());
            ps.setTimestamp(3, log.getStartTime());
            ps.setString(4, log.getState().name());
            ps.setObject(5, log.getWorkflowInstanceId());
            ps.setObject(6, log.getParentLogId());
            ps.setString(7, log.getTaskPattern() != null ? log.getTaskPattern().name() : null);
            ps.setString(8, log.getRtnMsg());
            ps.setString(9, log.getExMsg());
            ps.setString(10, log.getWorkflowNodeId());
            ps.setString(11, log.getParameters());
            ps.setString(12, log.getInstanceId());
            ps.setInt(13,log.getAttemptNumber());
            return ps;
        }, keyHolder);
        log.setLogId(keyHolder.getKey().longValue());
        return log;
    }

    @Override
    public Optional<TaskExecuteLog> findById(Long logId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, rowMapper, logId));
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
        return jdbcTemplate.query(SELECT_BY_TASK_ID_SQL, rowMapper, taskId);
    }

    @Override
    public int update(TaskExecuteLog log) {
        return jdbcTemplate.update(UPDATE_SQL,
                log.getTaskId(), log.getWorkflowId(), log.getStartTime(), log.getEndTime(),
                log.getState().name(), log.getRtnMsg(), log.getExMsg(), log.getWorkflowInstanceId(),
                log.getParentLogId(), log.getTaskPattern().name(), log.getWorkflowNodeId(),
                log.getParameters(), log.getInstanceId(),
                log.getLogId());
    }

    @Override
    public void updateLogStatus(Long logId, ExecutionState state, String rtnMsg, String exMsg) {
        String finalRtnMsg = rtnMsg;
        String finalExMsg = exMsg;

        if (finalRtnMsg != null && finalRtnMsg.length() > 1950) {
            logger.warn("rtnMsg for log_id {} is too long ({} chars), truncating.", logId, finalRtnMsg.length());
            finalRtnMsg = finalRtnMsg.substring(0, 1950) + "...";
        }
        if (finalExMsg != null && finalExMsg.length() > 1950) {
            logger.warn("exMsg for log_id {} is too long ({} chars), truncating.", logId, finalExMsg.length());
            finalExMsg = finalExMsg.substring(0, 1950) + "...";
        }

        try {
            jdbcTemplate.update(UPDATE_LOG_STATUS_SQL, state.name(), finalRtnMsg, finalExMsg, logId);
        } catch (Exception e) {
            logger.error("Error updating log status for log_id {}: {}", logId, e.getMessage(), e);
        }
    }

    @Override
    public void updateLogRtnMsg(Long logId, String rtnMsg) {
        String sql = "UPDATE task_execute_log SET rtn_msg = ? WHERE log_id = ?";
        try {
            String messageToSave = rtnMsg;
            if (rtnMsg != null && rtnMsg.length() > 2000) {
                messageToSave = rtnMsg.substring(0, 1997) + "...";
            }
            jdbcTemplate.update(sql, messageToSave, logId);
        } catch (Exception e) {
            logger.error("Error updating rtn_msg for log_id {}: {}", logId, e.getMessage(), e);
        }
    }

    @Override
    public void updateState(Long logId, ExecutionState state) {
        String sql = "UPDATE task_execute_log SET state = ? WHERE log_id = ?";
        try {
            jdbcTemplate.update(sql, state.name(), logId);
        } catch (Exception e) {
            logger.error("Error updating state for log_id {}: {}", logId, e.getMessage(), e);
        }
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
        String sql = "SELECT AVG(CAST(TIMESTAMPDIFF(MILLISECOND, start_time, end_time) AS DOUBLE)) " +
                "FROM task_execute_log " +
                "WHERE task_id = ? AND state = 'SUCCESS' AND start_time IS NOT NULL AND end_time IS NOT NULL";
        try {
            return jdbcTemplate.queryForObject(sql, Double.class, taskId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            logger.error("Error calculating average execution time for task ID {}: {}. SQL: {}", taskId, e.getMessage(), sql, e);
            String h2Sql = "SELECT AVG(DATEDIFF('MILLISECOND', start_time, end_time)) " +
                    "FROM task_execute_log " +
                    "WHERE task_id = ? AND state = 'SUCCESS' AND start_time IS NOT NULL AND end_time IS NOT NULL";
            try {
                return jdbcTemplate.queryForObject(h2Sql, Double.class, taskId);
            } catch (Exception e2) {
                logger.error("Error calculating average execution time for task ID {} with H2 SQL: {}. SQL: {}", taskId, e2.getMessage(), h2Sql, e);
                return null;
            }
        }
    }

    @Override
    public Long getTotalRuns(Integer taskId) {
        String sql = "SELECT COUNT(*) FROM task_execute_log WHERE task_id = ?";
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, taskId);
            return count != null ? count : 0L;
        } catch (EmptyResultDataAccessException e) {
            return 0L;
        } catch (Exception e) {
            logger.error("Error getting total runs for task ID {}: {}", taskId, e.getMessage(), e);
            return 0L;
        }
    }

    @Override
    public List<Map<String, Object>> getPerTaskSuccessFailureCounts() {
        String sql = "SELECT l.task_id, tc.task_name, " +
                "SUM(CASE WHEN l.state = 'SUCCESS' THEN 1 ELSE 0 END) as success_count, " +
                "SUM(CASE WHEN l.state = 'FAILED' THEN 1 ELSE 0 END) as failed_count " +
                "FROM task_execute_log l " +
                "JOIN task_config tc ON l.task_id = tc.task_id " +
                "GROUP BY l.task_id, tc.task_name " +
                "ORDER BY tc.task_name ASC";
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            logger.error("Error fetching per-task success/failure counts: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Map<String, Object>> getTopNAverageExecutionTimes(int limit) {
        String sql = "SELECT l.task_id, tc.task_name, " +
                "AVG(TIMESTAMPDIFF(MILLISECOND, l.start_time, l.end_time)) as avg_duration_ms " +
                "FROM task_execute_log l " +
                "JOIN task_config tc ON l.task_id = tc.task_id " +
                "WHERE l.state = 'SUCCESS' AND l.start_time IS NOT NULL AND l.end_time IS NOT NULL " +
                "GROUP BY l.task_id, tc.task_name " +
                "ORDER BY avg_duration_ms DESC " +
                "LIMIT ?";
        try {
            return jdbcTemplate.queryForList(sql, limit);
        } catch (Exception e) {
            logger.error("Error fetching top N average execution times with primary SQL: {}. Trying H2 specific.", e.getMessage());
            String h2Sql = "SELECT l.task_id, tc.task_name, " +
                    "AVG(CAST(DATEDIFF('MILLISECOND', l.start_time, l.end_time) AS DOUBLE)) as avg_duration_ms " +
                    "FROM task_execute_log l " +
                    "JOIN task_config tc ON l.task_id = tc.task_id " +
                    "WHERE l.state = 'SUCCESS' AND l.start_time IS NOT NULL AND l.end_time IS NOT NULL " +
                    "GROUP BY l.task_id, tc.task_name " +
                    "ORDER BY avg_duration_ms DESC " +
                    "LIMIT ?";
            try {
                logger.info("Attempting H2-specific SQL for getTopNAverageExecutionTimes.");
                return jdbcTemplate.queryForList(h2Sql, limit);
            } catch (Exception e2) {
                logger.error("Error fetching top N average execution times with H2-specific SQL: {}", e2.getMessage(), e2);
                return new ArrayList<>();
            }
        }
    }

    @Override
    public List<TaskExecuteLog> findByParentExecuteNo(Long parentExecuteNo) {
        String sql = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE parent_log_id = ? ORDER BY log_id ASC";
        try {
            return jdbcTemplate.query(sql, rowMapper, parentExecuteNo);
        } catch (Exception e) {
            logger.error("Error fetching logs by parent_log_id {}: {}", parentExecuteNo, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<TaskExecuteLog> findByParentExecuteNoAndTaskId(Long parentExecuteNo, int taskId) {
        String sql = "SELECT " + LOG_COLUMNS +
                " FROM task_execute_log " +
                "WHERE parent_log_id = ? AND task_id = ? " +
                "ORDER BY log_id ASC";
        try {
            return jdbcTemplate.query(sql, rowMapper, parentExecuteNo, taskId);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No logs found for parentExecuteNo {} and taskId {} (EmptyResultDataAccessException). Returning empty list.", parentExecuteNo, taskId);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("Error fetching logs by parentExecuteNo {} and taskId {}: {}", parentExecuteNo, taskId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public Optional<TaskExecuteLog> findLatestTerminalLogForWorkflowNode(Long parentWorkflowLogId, String workflowNodeId) {
        String sql = "SELECT " + LOG_COLUMNS + " FROM task_execute_log " +
                "WHERE parent_log_id = ? " +
                "  AND workflow_node_id = ? " +
                "  AND state IN ('SUCCESS', 'FAILED', 'TIMED_OUT', 'CANCELLED') " +
                "ORDER BY log_id DESC " +
                "LIMIT 1";
        try {
            TaskExecuteLog log = jdbcTemplate.queryForObject(sql, rowMapper, parentWorkflowLogId, workflowNodeId);
            return Optional.ofNullable(log);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error in findLatestTerminalLogForWorkflowNode for parentWorkflowLogId {} and workflowNodeId {}: {}",
                    parentWorkflowLogId, workflowNodeId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public long countSuccessfulExecutionsByNodeId(Long workflowInstanceId, List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return 0;
        }
        String inSql = String.join(",", java.util.Collections.nCopies(nodeIds.size(), "?"));
        String sql = String.format("SELECT COUNT(DISTINCT workflow_node_id) FROM task_execute_log WHERE workflow_instance_id = ? AND state = 'SUCCESS' AND workflow_node_id IN (%s)", inSql);

        List<Object> params = new ArrayList<>();
        params.add(workflowInstanceId);
        params.addAll(nodeIds);

        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count != null ? count : 0;
    }

    @Override
    public long countRunningTasksByInstanceId(Long workflowInstanceId) {
        String sql = "SELECT COUNT(*) FROM task_execute_log WHERE workflow_instance_id = ? AND state = 'RUNNING'";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, workflowInstanceId);
        return count != null ? count : 0;
    }
}
