package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.WorkflowInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class TaskWorkflowInstanceDaoImpl implements TaskWorkflowInstanceDao {

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<WorkflowInstance> rowMapper = (rs, rowNum) -> {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(rs.getInt("id"));
        instance.setWorkflowId(rs.getInt("workflow_id"));
        instance.setStatus(rs.getString("status"));
        instance.setCreateTime(rs.getTimestamp("create_time"));
        instance.setUpdateTime(rs.getTimestamp("update_time"));
        return instance;
    };

    @Override
    public int create(WorkflowInstance instance) {
        String sql = "INSERT INTO task_workflow_instance (workflow_id, status, create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, instance.getWorkflowId());
            ps.setString(2, instance.getStatus());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    @Override
    public Optional<WorkflowInstance> findById(int id) {
        String sql = "SELECT * FROM task_workflow_instance WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, new Object[]{id}, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int update(WorkflowInstance instance) {
        String sql = "UPDATE task_workflow_instance SET status = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
        return jdbcTemplate.update(sql, instance.getStatus(), instance.getId());
    }
}
