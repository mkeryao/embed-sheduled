package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskConfig;
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
import java.util.List;
import java.util.Optional;

@Repository
public class TaskConfigDaoImpl implements TaskConfigDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String BASE_COLUMNS = "task_id, task_name, task_group, cron_expression, task_type, bean_name, method_name, bean_parameters, http_url, http_method, http_headers, http_body, script_path, script_parameters, description, is_active, task_lock_name, task_lock_most_seconds, task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds, notify_success_user_ids, notify_failed_user_ids";
    private static final String WORKFLOW_JSON_COLUMNS = ", workflow_nodes, workflow_edges, global_parameters"; // schema: workflow_nodes, workflow_edges, global_parameters
    private static final String TIMESTAMP_COLUMNS = ", create_time, update_time";
    private static final String FULL_COLUMN_LIST = BASE_COLUMNS + WORKFLOW_JSON_COLUMNS + TIMESTAMP_COLUMNS;
    
    private static final String INSERT_BASE_COLUMNS = "task_name, task_group, cron_expression, task_type, bean_name, method_name, bean_parameters, http_url, http_method, http_headers, http_body, script_path, script_parameters, description, is_active, task_lock_name, task_lock_most_seconds, task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds, notify_success_user_ids, notify_failed_user_ids";
    private static final String INSERT_WORKFLOW_JSON_COLUMNS = ", workflow_nodes, workflow_edges, global_parameters";
    private static final String INSERT_COLUMNS = INSERT_BASE_COLUMNS + INSERT_WORKFLOW_JSON_COLUMNS + ", create_time, update_time";
    private static final String INSERT_PLACEHOLDERS = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"; // 27 placeholders now
    private static final String INSERT_SQL = "INSERT INTO task_config (" + INSERT_COLUMNS + ") VALUES (" + INSERT_PLACEHOLDERS + ")";
    
    private static final String UPDATE_BASE_SETTERS = "task_name=?, task_group=?, cron_expression=?, task_type=?, bean_name=?, method_name=?, bean_parameters=?, http_url=?, http_method=?, http_headers=?, http_body=?, script_path=?, script_parameters=?, description=?, is_active=?, task_lock_name=?, task_lock_most_seconds=?, task_calendar_group=?, task_exclude_times=?, start_date=?, end_date=?, execute_timeout_seconds=?, notify_success_user_ids=?, notify_failed_user_ids=?";
    private static final String UPDATE_WORKFLOW_JSON_SETTERS = ", workflow_nodes=?, workflow_edges=?, global_parameters=?";
    private static final String UPDATE_SETTERS = UPDATE_BASE_SETTERS + UPDATE_WORKFLOW_JSON_SETTERS + ", update_time=CURRENT_TIMESTAMP";
    private static final String UPDATE_SQL = "UPDATE task_config SET " + UPDATE_SETTERS + " WHERE task_id=?";

    private static final String SELECT_BY_ID_SQL = "SELECT " + FULL_COLUMN_LIST + " FROM task_config WHERE task_id=?";
    private static final String SELECT_ALL_SQL = "SELECT " + FULL_COLUMN_LIST + " FROM task_config";
    private static final String SELECT_ALL_ACTIVE_SQL = "SELECT " + FULL_COLUMN_LIST + " FROM task_config WHERE is_active=TRUE";
    private static final String SELECT_BY_GROUP_AND_NAME_SQL = "SELECT " + FULL_COLUMN_LIST + " FROM task_config WHERE task_group=? AND task_name=?";
    private static final String DELETE_BY_ID_SQL = "DELETE FROM task_config WHERE task_id=?";
    private static final String UPDATE_STATUS_SQL = "UPDATE task_config SET is_active=?, update_time=CURRENT_TIMESTAMP WHERE task_id=?";


    private final RowMapper<TaskConfig> rowMapper = (rs, rowNum) -> {
        TaskConfig task = new TaskConfig();
        task.setTaskId(rs.getInt("task_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setTaskGroup(rs.getString("task_group"));
        task.setCronExpression(rs.getString("cron_expression"));
        task.setTaskType(rs.getInt("task_type"));
        task.setBeanName(rs.getString("bean_name"));
        task.setMethodName(rs.getString("method_name"));
        task.setBeanParameters(rs.getString("bean_parameters"));
        task.setHttpUrl(rs.getString("http_url"));
        task.setHttpMethod(rs.getString("http_method"));
        task.setHttpHeaders(rs.getString("http_headers"));
        task.setHttpBody(rs.getString("http_body"));
        task.setScriptPath(rs.getString("script_path"));
        task.setScriptParameters(rs.getString("script_parameters"));
        task.setDescription(rs.getString("description"));
        task.setActive(rs.getBoolean("is_active"));
        task.setTaskLockName(rs.getString("task_lock_name"));
        task.setTaskLockMostSeconds(rs.getObject("task_lock_most_seconds", Integer.class));
        task.setTaskCalendarGroup(rs.getString("task_calendar_group"));
        task.setTaskExcludeTimes(rs.getString("task_exclude_times"));
        task.setStartDate(rs.getDate("start_date"));
        task.setEndDate(rs.getDate("end_date"));
        task.setExecuteTimeoutSeconds(rs.getObject("execute_timeout_seconds", Integer.class));
        task.setNotifySuccessUserIds(rs.getString("notify_success_user_ids"));
        task.setNotifyFailedUserIds(rs.getString("notify_failed_user_ids"));
        task.setWorkflowNodesJson(rs.getString("workflow_nodes"));
        task.setWorkflowEdgesJson(rs.getString("workflow_edges"));
        task.setGlobalParametersJson(rs.getString("global_parameters"));
        task.setCreateTime(rs.getTimestamp("create_time"));
        task.setUpdateTime(rs.getTimestamp("update_time"));
        return task;
    };

    @Override
    public TaskConfig save(TaskConfig taskConfig) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, taskConfig.getTaskName());
            ps.setString(2, taskConfig.getTaskGroup());
            ps.setString(3, taskConfig.getCronExpression());
            ps.setInt(4, taskConfig.getTaskType());
            ps.setString(5, taskConfig.getBeanName());
            ps.setString(6, taskConfig.getMethodName());
            ps.setString(7, taskConfig.getBeanParameters());
            ps.setString(8, taskConfig.getHttpUrl());
            ps.setString(9, taskConfig.getHttpMethod());
            ps.setString(10, taskConfig.getHttpHeaders());
            ps.setString(11, taskConfig.getHttpBody());
            ps.setString(12, taskConfig.getScriptPath());
            ps.setString(13, taskConfig.getScriptParameters());
            ps.setString(14, taskConfig.getDescription());
            ps.setBoolean(15, taskConfig.isActive());
            ps.setString(16, taskConfig.getTaskLockName());
            setObjectOrNull(ps, 17, taskConfig.getTaskLockMostSeconds(), java.sql.Types.INTEGER);
            ps.setString(18, taskConfig.getTaskCalendarGroup());
            ps.setString(19, taskConfig.getTaskExcludeTimes());
            ps.setDate(20, taskConfig.getStartDate());
            ps.setDate(21, taskConfig.getEndDate());
            setObjectOrNull(ps, 22, taskConfig.getExecuteTimeoutSeconds(), java.sql.Types.INTEGER);
            ps.setString(23, taskConfig.getNotifySuccessUserIds());
            ps.setString(24, taskConfig.getNotifyFailedUserIds());
            ps.setString(25, taskConfig.getWorkflowNodesJson());
            ps.setString(26, taskConfig.getWorkflowEdgesJson());
            ps.setString(27, taskConfig.getGlobalParametersJson());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            taskConfig.setTaskId(keyHolder.getKey().intValue());
        }
        return taskConfig;
    }

    @Override
    public Optional<TaskConfig> findById(Integer taskId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, new Object[]{taskId}, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskConfig> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    @Override
    public List<TaskConfig> findAllActiveTasks() {
        return jdbcTemplate.query(SELECT_ALL_ACTIVE_SQL, rowMapper);
    }

    @Override
    public Optional<TaskConfig> findByTaskGroupAndTaskName(String taskGroup, String taskName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_GROUP_AND_NAME_SQL, new Object[]{taskGroup, taskName}, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int update(TaskConfig taskConfig) {
        return jdbcTemplate.update(UPDATE_SQL,
                taskConfig.getTaskName(), taskConfig.getTaskGroup(), taskConfig.getCronExpression(),
                taskConfig.getTaskType(), taskConfig.getBeanName(), taskConfig.getMethodName(),
                taskConfig.getBeanParameters(), taskConfig.getHttpUrl(), taskConfig.getHttpMethod(),
                taskConfig.getHttpHeaders(), taskConfig.getHttpBody(), taskConfig.getScriptPath(),
                taskConfig.getScriptParameters(), taskConfig.getDescription(), taskConfig.isActive(),
                taskConfig.getTaskLockName(), taskConfig.getTaskLockMostSeconds(),
                taskConfig.getTaskCalendarGroup(), taskConfig.getTaskExcludeTimes(),
                taskConfig.getStartDate(), taskConfig.getEndDate(), taskConfig.getExecuteTimeoutSeconds(),
                taskConfig.getNotifySuccessUserIds(), taskConfig.getNotifyFailedUserIds(),
                taskConfig.getWorkflowNodesJson(), taskConfig.getWorkflowEdgesJson(), taskConfig.getGlobalParametersJson(),
                taskConfig.getTaskId());
    }

    // Helper method to set object or null
    private void setObjectOrNull(PreparedStatement ps, int parameterIndex, Object value, int sqlType) throws SQLException {
        if (value != null) {
            ps.setObject(parameterIndex, value);
        } else {
            ps.setNull(parameterIndex, sqlType);
        }
    }

    @Override
    public int deleteById(Integer taskId) {
        return jdbcTemplate.update(DELETE_BY_ID_SQL, taskId);
    }

    @Override
    public void updateTaskStatus(Integer taskId, boolean isActive) {
        jdbcTemplate.update(UPDATE_STATUS_SQL, isActive, taskId);
    }
}
