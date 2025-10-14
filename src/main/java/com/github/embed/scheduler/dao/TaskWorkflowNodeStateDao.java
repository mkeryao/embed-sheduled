package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskWorkflowNodeState;

import java.util.List;

public interface TaskWorkflowNodeStateDao {

    void batchCreate(List<TaskWorkflowNodeState> nodeStates);

    int decrementAndGetPendingParents(Long workflowInstanceId, String nodeId);

    void updateStatus(Long workflowInstanceId, String nodeId, String status);

    boolean isWorkflowComplete(Long workflowInstanceId);
}
