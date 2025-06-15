package com.example.taskscheduler.dto.workflow;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a node within a workflow definition. Each node typically
 * corresponds to a specific task (a
 * {@link com.example.taskscheduler.entity.TaskConfig} of type BEAN) to be
 * executed. Nodes can have parameters that override or supplement the
 * referenced task's defaults. The execution flow (transitions to other nodes)
 * is defined by {@link WorkflowEdge}s.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    private String id ;

    /**
     * Unique identifier for this node within the workflow. Used in
     * {@link WorkflowEdge} to define transitions.
     */
    private String nodeId;

    /**
     * The ID of the {@link com.example.taskscheduler.entity.TaskConfig} (must
     * be a BEAN task) that this node will execute.
     */
    private Integer taskConfigId;

    /**
     * An optional descriptive name for this workflow node.
     */
    private String nodeName;

    private Double x;

    private Double y;

    private String type;

    /**
     * Optional parameters for this specific node execution. These parameters
     * can override or supplement the default `beanParameters` of the referenced
     * {@link com.example.taskscheduler.entity.TaskConfig}. Values in this map
     * can use templating (e.g., `${variable_name}`) to refer to data from the
     * workflow context (global parameters or outputs from previous nodes like
     * `${nodeA_status}`).
     */
    private Map<String, Object> parameters;
}
