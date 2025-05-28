package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskExecuteLog;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;
import java.util.Optional;

@Repository
public class TaskExecuteLogDaoImpl implements TaskExecuteLogDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String LOG_COLUMNS = "log_id, task_id, start_time, end_time, state, ex_msg, instance_id, parent_execute_no, task_pattern";
    private static final String INSERT_SQL = "INSERT INTO task_execute_log (task_id, start_time, state, instance_id, parent_execute_no, task_pattern) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE task_execute_log SET task_id=?, start_time=?, end_time=?, state=?, ex_msg=?, instance_id=?, parent_execute_no=?, task_pattern=? WHERE log_id=?";
    private static final String SELECT_BY_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE log_id=?";
    private static final String SELECT_ALL_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log";
    private static final String SELECT_BY_TASK_ID_SQL = "SELECT " + LOG_COLUMNS + " FROM task_execute_log WHERE task_id=?";
    // UPDATE_LOG_STATUS_SQL might need to preserve parent_execute_no and task_pattern if they are set at creation
    // For now, it only updates state, end_time, ex_msg. This is usually fine as those fields don't change once set.
    private static final String UPDATE_LOG_STATUS_SQL = "UPDATE task_execute_log SET end_time=CURRENT_TIMESTAMP, state=?, ex_msg=? WHERE log_id=?";


    private final RowMapper<TaskExecuteLog> rowMapper = (rs, rowNum) -> {
        TaskExecuteLog log = new TaskExecuteLog();
        log.setLogId(rs.getInt("log_id"));
        log.setTaskId(rs.getInt("task_id"));
        log.setStartTime(rs.getTimestamp("start_time"));
        log.setEndTime(rs.getTimestamp("end_time"));
        log.setState(rs.getString("state"));
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
            // Handle nullable parentLogId and taskPattern
            if (log.getParentLogId() != null) {
                ps.setInt(5, log.getParentLogId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, log.getTaskPattern());
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
        } catch (Exception e) {
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
                log.getState(), log.getExMsg(), log.getInstanceId(),
                log.getParentLogId(), log.getTaskPattern(),
                log.getLogId());
    }
    
    @Override
    public void updateLogStatus(Integer logId, String state, String exMsg) {
        // This method intentionally does not update parentLogId or taskPattern
        jdbcTemplate.update(UPDATE_LOG_STATUS_SQL, state, exMsg, logId);
    }
}
