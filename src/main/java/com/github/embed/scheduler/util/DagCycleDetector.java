package com.github.embed.scheduler.util;

import com.github.embed.scheduler.dto.workflow.WorkflowEdge;
import com.github.embed.scheduler.dto.workflow.WorkflowNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
// import java.util.stream.Collectors; // Not strictly needed with current logic

public class DagCycleDetector {

    private static final int NOT_VISITED = 0; // White
    private static final int VISITING = 1;    // Gray
    private static final int VISITED = 2;     // Black

    public boolean hasCycle(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        if (nodes == null || nodes.isEmpty() || edges == null || edges.isEmpty()) {
            return false; // No cycle if no nodes or no edges
        }

        Map<String, List<String>> adj = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();

        for (WorkflowNode node : nodes) {
            if (node.getNodeId() == null || node.getNodeId().trim().isEmpty()) {
                // Invalid node definition, could throw an error or ignore
                // For cycle detection, an ID-less node is problematic.
                // Consider this a data validation issue outside cycle detection logic.
                // For now, skip if nodeId is null/empty as it can't participate in edges.
                continue;
            }
            nodeIds.add(node.getNodeId());
            adj.putIfAbsent(node.getNodeId(), new ArrayList<>());
        }

        if (nodeIds.isEmpty()) return false; // No valid nodes to form a cycle

        boolean hasActualEdges = false;
        for (WorkflowEdge edge : edges) {
            if (edge.getFromNodeId() == null || edge.getToNodeId() == null) {
                // Invalid edge definition
                continue;
            }
            // Ensure both from and to nodes exist in the declared nodes list
            if (nodeIds.contains(edge.getFromNodeId()) && nodeIds.contains(edge.getToNodeId())) {
                adj.computeIfAbsent(edge.getFromNodeId(), k -> new ArrayList<>()).add(edge.getToNodeId());
                hasActualEdges = true;
            } else {
                // Edge refers to a node not in workflowNodesJson, which is an inconsistency.
                // Depending on strictness, this could be an error or ignored for cycle detection.
                // For now, ignore such edges for cycle detection logic itself, but this indicates bad data.
                // Such edges cannot form part of a cycle among defined nodes.
            }
        }

        // If adj is empty after processing edges (e.g. all edges referred to non-existent nodes)
        // or if there are no actual connections made.
        if (!hasActualEdges) {
            // This means edges were defined but none were valid given the nodes,
            // or no edges were defined at all (initial check covers this).
            // Not a cycle.
            return false;
        }

        Map<String, Integer> visitStatus = new HashMap<>();
        for (String nodeId : nodeIds) { // Initialize all declared valid nodes
            visitStatus.put(nodeId, NOT_VISITED);
        }

        for (String nodeId : nodeIds) { // Iterate over all declared valid nodes
            if (visitStatus.get(nodeId) == NOT_VISITED) {
                if (dfs(nodeId, adj, visitStatus)) {
                    return true; // Cycle detected
                }
            }
        }
        return false; // No cycles found
    }

    private boolean dfs(String currentNodeId, Map<String, List<String>> adj, Map<String, Integer> visitStatus) {
        visitStatus.put(currentNodeId, VISITING); // Mark as gray

        List<String> neighbors = adj.getOrDefault(currentNodeId, new ArrayList<>());
        for (String neighborId : neighbors) {
            // If neighborId was part of an edge but not in the 'nodes' list (and thus not in visitStatus keys initially)
            // This case is handled by the edge processing logic where only edges between known nodes are added to adj.
            // So, all neighborId here should be in visitStatus.
            // However, if a node was defined but then removed from nodeIds due to being invalid, it might be an issue.
            // The current logic adds all valid nodeIds to visitStatus.
            // Edges pointing to nodes not in nodeIds are ignored when building adj list.
            // So, every neighborId here *should* be in visitStatus.

            if (!visitStatus.containsKey(neighborId)) {
                 // This implies neighborId was in an edge but not in the original nodes list.
                 // The adjacency list (adj) construction should ideally prevent this by only adding valid edges.
                 // If it still occurs, it's a data integrity issue. For cycle detection, we can skip.
                 continue;
            }

            if (visitStatus.get(neighborId) == VISITING) {
                return true; // Cycle detected: edge to a node currently in recursion stack
            }
            if (visitStatus.get(neighborId) == NOT_VISITED) {
                if (dfs(neighborId, adj, visitStatus)) {
                    return true; // Cycle detected in deeper DFS call
                }
            }
        }

        visitStatus.put(currentNodeId, VISITED); // Mark as black
        return false;
    }
}
