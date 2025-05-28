package com.example.taskscheduler.dto.workflow;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {
    private String fromNodeId;
    private String toNodeId;
    private String condition; // e.g., "SUCCESS", "FAILURE" - this will be deprecated by 'expression'
    private String expression; // e.g., "${NodeA_output.status == 'COMPLETED'}" or custom format
    private int priority = 0; // Lower number means higher priority
}
