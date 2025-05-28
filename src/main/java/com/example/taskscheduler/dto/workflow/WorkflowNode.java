package com.example.taskscheduler.dto.workflow;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {
    private String nodeId; // Unique within the workflow
    private Integer taskConfigId; // References a TaskConfig of type 'BEAN'
    private String nodeName; // Optional description
    private Map<String, Object> parameters; // Optional, to override or supplement task's default bean_parameters. Can use templating.
    // onSuccess and onFailure are removed, replaced by WorkflowEdges
}
