package com.github.embed.scheduler.entity;

import lombok.Data;

@Data
public class TaskWorkflowNodeState {
    private Long id;
    private Long workflowInstanceId;
    private String nodeId;
    private int pendingParents;
    private String status;
}
