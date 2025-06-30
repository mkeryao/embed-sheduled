package com.github.embed.scheduler.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an edge or transition between two {@link WorkflowNode}s in a workflow definition.
 * Edges define the flow of execution, potentially based on conditions or expressions evaluated
 * against the workflow context.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {

    private String id;
    /**
     * The ID of the source {@link WorkflowNode} from which this edge originates.
     */
    private String fromNodeId;

    /**
     * The ID of the target {@link WorkflowNode} to which this edge leads.
     * Can also be a special keyword like "END" to signify workflow completion for this path.
     */
    private String toNodeId;

    /**
     * A simple condition string, e.g., "SUCCESS", "FAILURE".
     * This is primarily for basic conditional transitions based on the status of the `fromNodeId`.
     * For more complex logic, the {@link #expression} field should be used.
     * If both `condition` and `expression` are present, `expression` typically takes precedence.
     * @deprecated Prefer using {@link #expression} for more flexible conditional logic.
     */
    @Deprecated
    private String condition;

    /**
     * A more powerful expression (e.g., using Spring Expression Language (SpEL) or a custom format)
     * that is evaluated to determine if this edge should be taken.
     * The expression can reference data from the workflow context, such as global parameters
     * or the status/output of previously executed nodes (e.g., "${nodeA_status} == 'SUCCESS'").
     * If the expression evaluates to true, this edge is considered a valid path.
     */
    private String expression;

    /**
     * The priority of this edge when multiple outgoing edges from the same node might have their
     * conditions/expressions evaluate to true. Lower numbers indicate higher priority.
     * The workflow execution service will typically choose the highest priority edge among valid ones.
     * Default is 0.
     */
    private int priority = 0;
}
