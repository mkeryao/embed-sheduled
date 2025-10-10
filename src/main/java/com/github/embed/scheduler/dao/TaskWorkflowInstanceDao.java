package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.WorkflowInstance;

import java.util.Optional;

public interface TaskWorkflowInstanceDao {
    int create(WorkflowInstance instance);
    Optional<WorkflowInstance> findById(int id);
    int update(WorkflowInstance instance);
}
