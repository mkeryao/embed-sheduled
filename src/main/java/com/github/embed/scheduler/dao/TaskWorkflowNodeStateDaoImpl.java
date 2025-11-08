package com.github.embed.scheduler.dao;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.github.embed.scheduler.entity.TaskWorkflowNodeState;
import com.github.embed.scheduler.enums.ExecutionState;

@Repository
public class TaskWorkflowNodeStateDaoImpl implements TaskWorkflowNodeStateDao {

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Override
    public void batchCreate(List<TaskWorkflowNodeState> states) {
        String sql = "INSERT INTO task_workflow_node_state (workflow_instance_id, node_id, pending_parents, status) VALUES (:workflowInstanceId, :nodeId, :pendingParents, :status)";
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(states.toArray());
        new NamedParameterJdbcTemplate(jdbcTemplate).batchUpdate(sql, batch);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int decrementAndGetPendingParents(Long workflowInstanceId, String nodeId) {
        String updateSql = "UPDATE task_workflow_node_state SET pending_parents = pending_parents - 1 WHERE workflow_instance_id = ? AND node_id = ?";
        jdbcTemplate.update(updateSql, workflowInstanceId, nodeId);

        String selectSql = "SELECT pending_parents FROM task_workflow_node_state WHERE workflow_instance_id = ? AND node_id = ?";
        Integer pendingCount = jdbcTemplate.queryForObject(selectSql, Integer.class, workflowInstanceId, nodeId);
        return pendingCount != null ? pendingCount : -1; // Return -1 or throw exception if not found
    }

    @Override
    public void updateStatus(Long workflowInstanceId, String nodeId, String status) {
        String sql = "UPDATE task_workflow_node_state SET status = ? WHERE workflow_instance_id = ? AND node_id = ?";
        jdbcTemplate.update(sql, status, workflowInstanceId, nodeId);
    }

    @Override
    public boolean isWorkflowComplete(Long workflowInstanceId) {
        String sql = "SELECT COUNT(*) FROM task_workflow_node_state WHERE workflow_instance_id = ? AND status NOT IN (?, ?)";
        Integer activeOrPendingCount = jdbcTemplate.queryForObject(sql, Integer.class, workflowInstanceId, ExecutionState.SUCCESS.name(), ExecutionState.FAILED.name());
        return activeOrPendingCount != null && activeOrPendingCount == 0;
    }


}
