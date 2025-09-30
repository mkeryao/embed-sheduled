package com.github.embed.scheduler.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException; // Import ExecutionMode
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.enums.ExecutionMode;

import javax.annotation.Resource;

/**
 * JDBC implementation of the {@link TaskConfigDao} interface.
 * Handles database operations for
 * {@link TaskConfig} entities
 * using Spring's {@link JdbcTemplate}.
 */
@Repository
public class TaskConfigDaoImpl implements TaskConfigDao {

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    // BASE_COLUMNS: added max_retry_attempts, retry_interval_seconds,
    // retry_interval_multiplier
    private static final String BASE_COLUMNS = "task_id, task_name, task_group, cron_expression, task_type, bean_name, method_name, bean_parameters, description, is_active, execution_mode, max_retry_attempts, retry_interval_seconds, retry_interval_multiplier, task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds, notify_success_user_ids, notify_failed_user_ids";
    private static final String WORKFLOW_JSON_COLUMNS = ", workflow_nodes, workflow_edges, global_parameters";
    private static final String TIMESTAMP_COLUMNS = ", create_time, update_time";
    private static final String FULL_COLUMN_LIST = BASE_COLUMNS + WORKFLOW_JSON_COLUMNS + TIMESTAMP_COLUMNS;

    // INSERT_BASE_COLUMNS: removed http/shell direct fields.
    // INSERT_BASE_COLUMNS: added max_retry_attempts, retry_interval_seconds,
    // retry_interval_multiplier (17 + 3 = 20 fields)
    private static final String INSERT_BASE_COLUMNS = "task_name, task_group, cron_expression, task_type, bean_name, method_name, bean_parameters, description, is_active, execution_mode, max_retry_attempts, retry_interval_seconds, retry_interval_multiplier, task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds, notify_success_user_ids, notify_failed_user_ids";
    private static final String INSERT_WORKFLOW_JSON_COLUMNS = ", workflow_nodes, workflow_edges, global_parameters";
    private static final String INSERT_COLUMNS = INSERT_BASE_COLUMNS + INSERT_WORKFLOW_JSON_COLUMNS
            + ", create_time, update_time";
    // Placeholders: 20 base fields + 3 workflow fields = 23 fields.
    private static final String ACTUAL_INSERT_PLACEHOLDERS = String.join(",", java.util.Collections.nCopies(23, "?"))
            + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP";
    private static final String INSERT_SQL = "INSERT INTO task_config (" + INSERT_COLUMNS + ") VALUES ("
            + ACTUAL_INSERT_PLACEHOLDERS + ")";

    // UPDATE_BASE_SETTERS: added max_retry_attempts=?, retry_interval_seconds=?,
    // retry_interval_multiplier=?
    private static final String UPDATE_BASE_SETTERS = "task_name=?, task_group=?, cron_expression=?, task_type=?, bean_name=?, method_name=?, bean_parameters=?, description=?, is_active=?, execution_mode=?, max_retry_attempts=?, retry_interval_seconds=?, retry_interval_multiplier=?, task_calendar_group=?, task_exclude_times=?, start_date=?, end_date=?, execute_timeout_seconds=?, notify_success_user_ids=?, notify_failed_user_ids=?";
    private static final String UPDATE_WORKFLOW_JSON_SETTERS = ", workflow_nodes=?, workflow_edges=?, global_parameters=?";
    private static final String UPDATE_SETTERS = UPDATE_BASE_SETTERS + UPDATE_WORKFLOW_JSON_SETTERS
            + ", update_time=CURRENT_TIMESTAMP";
    private static final String UPDATE_SQL = "UPDATE task_config SET " + UPDATE_SETTERS + " WHERE task_id=?";

    private static final String SELECT_BY_ID_SQL = "SELECT " + FULL_COLUMN_LIST + " FROM task_config WHERE task_id=?";
    // SELECT_ALL_SQL will be replaced by findByFilters logic
    private static final String SELECT_ALL_ACTIVE_SQL = "SELECT " + FULL_COLUMN_LIST
            + " FROM task_config WHERE is_active=TRUE AND task_group=?";
    private static final String SELECT_BY_GROUP_AND_NAME_SQL = "SELECT " + FULL_COLUMN_LIST
            + " FROM task_config WHERE task_group=? AND task_name=?";
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
        // httpUrl, httpMethod, httpHeaders, httpBody, scriptPath, scriptParameters are
        // removed
        task.setDescription(rs.getString("description"));
        task.setActive(rs.getBoolean("is_active"));
        // Handle ExecutionMode enum
        String executionModeStr = rs.getString("execution_mode");
        if (executionModeStr != null) {
            try {
                task.setExecutionMode(ExecutionMode.valueOf(executionModeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                task.setExecutionMode(ExecutionMode.BROADCAST);
            }
        } else {
            task.setExecutionMode(ExecutionMode.BROADCAST);
        }
        task.setMaxRetryAttempts(rs.getObject("max_retry_attempts", Integer.class));
        task.setRetryIntervalSeconds(rs.getObject("retry_interval_seconds", Integer.class));
        task.setRetryIntervalMultiplier(rs.getObject("retry_interval_multiplier", Float.class));
        task.setTaskCalendarGroup(rs.getString("task_calendar_group"));
        task.setTaskExcludeTimes(rs.getString("task_exclude_times"));
        task.setStartDate(rs.getTimestamp("start_date"));
        task.setEndDate(rs.getTimestamp("end_date"));
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
            // Indices shift here: http/shell fields removed (were 8-13)
            ps.setString(8, taskConfig.getDescription());
            ps.setBoolean(9, taskConfig.isActive());
            ps.setString(10, taskConfig.getExecutionMode() != null ? taskConfig.getExecutionMode().name()
                    : ExecutionMode.BROADCAST.name());
            setObjectOrNull(ps, 11, taskConfig.getMaxRetryAttempts(), java.sql.Types.INTEGER);
            setObjectOrNull(ps, 12, taskConfig.getRetryIntervalSeconds(), java.sql.Types.INTEGER);
            setObjectOrNull(ps, 13, taskConfig.getRetryIntervalMultiplier(), java.sql.Types.FLOAT);
            ps.setString(14, taskConfig.getTaskCalendarGroup());
            ps.setString(15, taskConfig.getTaskExcludeTimes());
            ps.setTimestamp(16, taskConfig.getStartDate());
            ps.setTimestamp(17, taskConfig.getEndDate());
            setObjectOrNull(ps, 18, taskConfig.getExecuteTimeoutSeconds(), java.sql.Types.INTEGER);
            ps.setString(19, taskConfig.getNotifySuccessUserIds());
            ps.setString(20, taskConfig.getNotifyFailedUserIds());
            // Workflow fields start at 21
            ps.setString(21, taskConfig.getWorkflowNodesJson());
            ps.setString(22, taskConfig.getWorkflowEdgesJson());
            ps.setString(23, taskConfig.getGlobalParametersJson());
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
            return Optional
                    .ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, new Object[] { taskId }, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskConfig> findByFilters(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT " + FULL_COLUMN_LIST + " FROM task_config WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (filters != null) {
            String taskName = (String) filters.get("taskName");
            if (org.springframework.util.StringUtils.hasText(taskName)) {
                sql.append(" AND task_name LIKE ?");
                params.add("%" + taskName + "%");
            }

            String taskGroup = (String) filters.get("taskGroup");
            if (org.springframework.util.StringUtils.hasText(taskGroup)) {
                sql.append(" AND task_group = ?");
                params.add(taskGroup);
            }

            Integer taskType = (Integer) filters.get("taskType");
            if (taskType != null) {
                sql.append(" AND task_type = ?");
                params.add(taskType);
            }

            Boolean isActive = (Boolean) filters.get("isActive");
            if (isActive != null) {
                sql.append(" AND is_active = ?");
                params.add(isActive);
            }
        }

        sql.append(" ORDER BY task_id DESC");

        return jdbcTemplate.query(sql.toString(), params.toArray(), rowMapper);
    }

    @Override
    public List<TaskConfig> findAllActiveTasks(String taskGroup) {
        return jdbcTemplate.query(SELECT_ALL_ACTIVE_SQL, new Object[]{taskGroup}, rowMapper);
    }

    @Override
    public Optional<TaskConfig> findByTaskGroupAndTaskName(String taskGroup, String taskName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_GROUP_AND_NAME_SQL,
                    new Object[] { taskGroup, taskName }, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int update(TaskConfig taskConfig) {
        return jdbcTemplate.update(UPDATE_SQL,
                taskConfig.getTaskName(), taskConfig.getTaskGroup(), taskConfig.getCronExpression(),
                taskConfig.getTaskType(), taskConfig.getBeanName(), taskConfig.getMethodName(),
                taskConfig.getBeanParameters(),
                taskConfig.getDescription(), taskConfig.isActive(),
                taskConfig.getExecutionMode() != null ? taskConfig.getExecutionMode().name()
                        : ExecutionMode.BROADCAST.name(),
                taskConfig.getMaxRetryAttempts(), taskConfig.getRetryIntervalSeconds(),
                taskConfig.getRetryIntervalMultiplier(),
                taskConfig.getTaskCalendarGroup(), taskConfig.getTaskExcludeTimes(),
                taskConfig.getStartDate(), taskConfig.getEndDate(), taskConfig.getExecuteTimeoutSeconds(),
                taskConfig.getNotifySuccessUserIds(), taskConfig.getNotifyFailedUserIds(),
                taskConfig.getWorkflowNodesJson(), taskConfig.getWorkflowEdgesJson(),
                taskConfig.getGlobalParametersJson(),
                taskConfig.getTaskId());
    }

    private void setObjectOrNull(PreparedStatement ps, int parameterIndex, Object value, int sqlType)
            throws SQLException {
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
