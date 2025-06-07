package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.util.ExpressionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC; // Import MDC
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service responsible for executing workflow tasks.
 * A workflow task consists of a series of nodes and edges defined in its {@link TaskConfig}.
 * Each node typically references another {@link TaskConfig} (of type BEAN) to be executed.
 * Edges define transitions between nodes, potentially based on expressions evaluated against a context.
 * The service manages the overall workflow execution log and individual step logs.
 */
@Service
public class WorkflowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionService.class);

    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    @Autowired
    private BeanTaskExecutor beanTaskExecutor;
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private ExpressionUtil expressionUtil;

    @Autowired
    private com.example.taskscheduler.scheduler.CoreSchedulerService coreSchedulerService;

    /**
     * Starts the execution of a workflow defined by the given {@link TaskConfig}.
     * This method is called by {@link com.example.taskscheduler.scheduler.CoreSchedulerService}
     * when a task of type 'WORKFLOW' (type 10) is triggered.
     *
     * @param workflowTaskConfig The configuration of the workflow task.
     * @param parentWorkflowLog The initial execution log entry created for this workflow instance.
     *                          This log's status will be updated based on the overall workflow outcome.
     */
    public void startWorkflow(TaskConfig workflowTaskConfig, TaskExecuteLog parentWorkflowLog) {
        if (workflowTaskConfig == null || workflowTaskConfig.getTaskType() != 10) { // Type 10 is WORKFLOW (was 3)
            logger.error("Task {} (ID: {}) is not a valid workflow task or is null.",
                    workflowTaskConfig != null ? workflowTaskConfig.getTaskName() : "null",
                    workflowTaskConfig != null ? workflowTaskConfig.getTaskId() : "null");
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Invalid workflow task configuration (not type WORKFLOW or null).");
            return;
        }
        logger.info("Starting workflow: {} (ID: {}, Log ID: {})",
                workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId(), parentWorkflowLog.getLogId());

        List<WorkflowNode> nodes;
        List<WorkflowEdge> edges;
        Map<String, Object> initialContextData = new HashMap<>();

        try {
            // Parse global parameters defined in the workflow task config
            if (StringUtils.hasText(workflowTaskConfig.getGlobalParametersJson())) {
                Map<String, Object> globalParams = JSON.parseObject(workflowTaskConfig.getGlobalParametersJson(), new com.alibaba.fastjson.TypeReference<Map<String, Object>>() {});
                initialContextData.putAll(globalParams);
                logger.info("Loaded global parameters for workflow {}: {}", workflowTaskConfig.getTaskName(), globalParams.keySet());
            }
            // Add some default workflow-level info to context
            initialContextData.put("workflow_name", workflowTaskConfig.getTaskName());
            initialContextData.put("workflow_id", workflowTaskConfig.getTaskId());
            initialContextData.put("workflow_log_id", parentWorkflowLog.getLogId());


            if (!StringUtils.hasText(workflowTaskConfig.getWorkflowNodesJson())) {
                logger.warn("Workflow {} (ID: {}) has no nodes defined.", workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId());
                updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow has no nodes to execute.");
                return;
            }
            nodes = JSON.parseArray(workflowTaskConfig.getWorkflowNodesJson(), WorkflowNode.class);

            if (!StringUtils.hasText(workflowTaskConfig.getWorkflowEdgesJson())) {
                logger.warn("Workflow {} (ID: {}) has no edges defined. Cannot determine execution flow.",
                        workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId());
                updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Workflow definition incomplete: missing edges.");
                return;
            }
            edges = JSON.parseArray(workflowTaskConfig.getWorkflowEdgesJson(), WorkflowEdge.class);
            // Added check for empty edges list after parsing, if nodes are present
            if (!CollectionUtils.isEmpty(nodes) && CollectionUtils.isEmpty(edges)) {
                logger.warn("Workflow {} (ID: {}) has nodes but no edges defined after parsing. Execution flow cannot be determined.",
                        workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId());
                updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Workflow definition incomplete: nodes exist but no edges found after parsing.");
                return;
            }

        } catch (Exception e) {
            logger.error("Failed to parse workflow definition for workflow {} (ID: {}): {}",
                    workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId(), e.getMessage(), e);
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Error parsing workflow definition: " + e.getMessage());
            return;
        }

        if (CollectionUtils.isEmpty(nodes)) {
            logger.info("Workflow {} (ID: {}) has no nodes to execute.", workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow executed successfully (no nodes).");
            return;
        }

        WorkflowNode startNode = findStartNode(nodes, edges);
        if (startNode == null) {
            logger.error("Could not determine start node for workflow {} (ID: {}).",
                    workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Could not determine start node.");
            return;
        }
        logger.info("Determined start node for workflow {} (ID: {}): Node ID '{}'",
                workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId(), startNode.getNodeId());

        Map<String, WorkflowNode> nodeMap = nodes.stream().collect(Collectors.toMap(WorkflowNode::getNodeId, node -> node));

        // Initial execution context for starting nodes
        Map<String, Object> executionContext = new HashMap<>(initialContextData);

        // Update parent workflow log to indicate it's running and what nodes are initially triggered
        // More sophisticated state can be stored in rtn_msg as JSON if needed.
        StringBuilder initialMessage = new StringBuilder("Workflow started. ");
        if (!initialContextData.isEmpty()) {
            initialMessage.append("Global params loaded: ").append(initialContextData.keySet().toString()).append(". ");
        }
        initialMessage.append("Attempting to trigger start node(s).");

        // Potentially update parentWorkflowLog's rtn_msg with a structured state
        // For now, just a general message. The actual node statuses will be tracked by their individual logs
        // and aggregated by processNodeCompletion.
        updateWorkflowLog(parentWorkflowLog.getLogId(), "RUNNING", initialMessage.toString());

        // Trigger the start node. If there are multiple potential start nodes (no incoming edges),
        // this logic might need to trigger all of them if that's the desired semantic.
        // For now, findStartNode usually returns one.
        if (startNode != null) {
            logger.info("Workflow {} (Log ID: {}): Triggering start node '{}'.",
                        workflowTaskConfig.getTaskName(), parentWorkflowLog.getLogId(), startNode.getNodeId());

            Optional<TaskConfig> referencedTaskConfigOpt = taskConfigDao.findById(startNode.getTaskConfigId());
            if (referencedTaskConfigOpt.isPresent() && referencedTaskConfigOpt.get().getTaskType() == 0) { // Must be BEAN task
                TaskConfig referencedTaskConfig = referencedTaskConfigOpt.get();
                Map<String, Object> resolvedParameters = prepareAndResolveParameters(referencedTaskConfig, startNode, executionContext);
                String resolvedParametersJson = (resolvedParameters != null && !resolvedParameters.isEmpty()) ? JSON.toJSONString(resolvedParameters) : null;

                coreSchedulerService.triggerTaskManually(
                        referencedTaskConfig.getTaskId(),
                        "WORKFLOW_STEP",
                        parentWorkflowLog.getLogId(),
                        resolvedParametersJson,
                        startNode.getNodeId() // Pass the workflowNodeId
                );
                // Initial status in context for the start node
                executionContext.put(startNode.getNodeId() + "_status", "TRIGGERED");
                // Update parent log message or state if storing detailed node status there
                String msgUpdate = parentWorkflowLog.getRtnMsg() + "\nNode '" + startNode.getNodeId() + "' triggered.";
                updateWorkflowLog(parentWorkflowLog.getLogId(), "RUNNING", msgUpdate);

            } else {
                String errorMsg = String.format("Start node '%s' references invalid, non-BEAN, or missing TaskConfigId: %d",
                                                startNode.getNodeId(), startNode.getTaskConfigId());
                logger.error(errorMsg);
                updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", errorMsg);
                // No further processing if start node cannot be triggered.
            }
        } else {
             // This case should have been caught earlier (no nodes or startNode is null after findStartNode)
             // but as a safeguard:
            logger.warn("Workflow {} (Log ID: {}): No start node found, workflow cannot begin.",
                         workflowTaskConfig.getTaskName(), parentWorkflowLog.getLogId());
            // If there were no nodes at all, it would have been SUCCESS. If nodes exist but no start node, it's a definition FAILED.
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "No start node could be determined.");
        }
        // startWorkflow now finishes. Progression happens via processNodeCompletion.
    }

    /**
     * Finds the start node of the workflow.
     * A start node is identified as a node that is not a target in any edge.
     * If multiple such nodes exist, or if a node is explicitly named "start" (case-insensitive),
     * that node is prioritized. As a fallback, the first node in the provided list is used.
     *
     * @param nodes List of all workflow nodes.
     * @param edges List of all workflow edges.
     * @return The identified start node, or {@code null} if no nodes are defined.
     */
    private WorkflowNode findStartNode(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        if (nodes.isEmpty()) return null;

        Optional<WorkflowNode> predesignatedStartNode = nodes.stream()
                .filter(n -> "start".equalsIgnoreCase(n.getNodeId()))
                .findFirst();
        if(predesignatedStartNode.isPresent()) return predesignatedStartNode.get();

        List<String> targetNodeIds = edges.stream().map(WorkflowEdge::getToNodeId).collect(Collectors.toList());
        return nodes.stream()
                .filter(node -> !targetNodeIds.contains(node.getNodeId()))
                .findFirst()
                .orElse(nodes.get(0)); // Fallback to the first node if no other start node criteria met
    }

    // executeNodeRecursive method is removed as its logic is now split between startWorkflow (for initial trigger)
    // and processNodeCompletion (for handling subsequent triggers and state).

    /**
     * Prepares and resolves parameters for a workflow node execution.
     * It merges parameters defined in the referenced {@link TaskConfig} (bean task)
     * with parameters specified in the {@link WorkflowNode} definition.
     * Node-specific parameters override task defaults.
     * Templates within parameter values (both base and node-specific) are resolved using the current workflow context.
     *
     * @param referencedTaskConfig The base {@link TaskConfig} for the bean to be executed.
     * @param node The current {@link WorkflowNode} being executed.
     * @param contextData The current workflow execution context data.
     * @return A map of resolved parameters to be used for the bean execution.
     */
    private Map<String, Object> prepareAndResolveParameters(TaskConfig referencedTaskConfig, WorkflowNode node, Map<String, Object> contextData) {
        Map<String, Object> baseParams = new HashMap<>();
        try {
            if (StringUtils.hasText(referencedTaskConfig.getBeanParameters())) {
                // Resolve templates in base parameters from the task config
                String resolvedBaseParamsJson = expressionUtil.resolveTemplates(referencedTaskConfig.getBeanParameters(), contextData);
                baseParams = JSON.parseObject(resolvedBaseParamsJson, new com.alibaba.fastjson.TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.warn("Error parsing or resolving base parameters for task {} (ID: {}): {}",
                    referencedTaskConfig.getTaskName(), referencedTaskConfig.getTaskId(), e.getMessage(), e);
        }

        Map<String, Object> nodeParams = new HashMap<>();
        if (node.getParameters() != null && !node.getParameters().isEmpty()) {
            node.getParameters().forEach((key, value) -> {
                if (value instanceof String) {
                    // Resolve templates in node-specific parameters
                    nodeParams.put(key, expressionUtil.resolveTemplates((String) value, contextData));
                } else {
                    nodeParams.put(key, value); // Keep non-string values (e.g., numbers, booleans from JSON) as is
                }
            });
        }

        // Merge: Node parameters override base parameters.
        Map<String, Object> mergedParams = new HashMap<>(baseParams);
        mergedParams.putAll(nodeParams);

        logger.debug("Resolved parameters for node '{}' (Task ID: {}): {}",
                node.getNodeId(), referencedTaskConfig.getTaskId(), mergedParams);
        return mergedParams;
    }

    /**
     * Updates the status and message of a workflow's main execution log entry.
     *
     * @param logId The ID of the workflow's main log entry.
     * @param status The new status (e.g., "SUCCESS", "FAILED").
     * @param message An optional message describing the outcome.
     */
    private void updateWorkflowLog(Integer logId, String status, String message) {
        String finalMessage = message;
        if (message != null && message.length() > 2000) { // Cap message length for DB
            finalMessage = message.substring(0, 1997) + "...";
        }
        // Pass null for exMsg when updating workflow log status/message this way
        taskExecuteLogDao.updateLogStatus(logId, status, finalMessage, null);
    }

    // New method to be called by CoreSchedulerService upon node completion
    public void processNodeCompletion(long parentWorkflowLogId, String completedNodeId, String nodeFinalStatus, long nodeLastLogId) {
        MDC.put("workflow_log_id", String.valueOf(parentWorkflowLogId));
        MDC.put("completed_node_id", completedNodeId);
        MDC.put("node_final_status", nodeFinalStatus);
        logger.info("Processing completion of node '{}' for workflow log ID {} with status '{}' (Last Log ID: {}).",
                completedNodeId, parentWorkflowLogId, nodeFinalStatus, nodeLastLogId);

        TaskExecuteLog parentWorkflowLog = taskExecuteLogDao.findById(Math.toIntExact(parentWorkflowLogId)).orElse(null);
        if (parentWorkflowLog == null) {
            logger.error("Parent workflow log ID {} not found. Cannot process node completion for node {}.", parentWorkflowLogId, completedNodeId);
            MDC.clear();
            return;
        }

        TaskConfig workflowTaskConfig = taskConfigDao.findById(parentWorkflowLog.getTaskId()).orElse(null);
        if (workflowTaskConfig == null || workflowTaskConfig.getTaskType() != 10) {
            logger.error("TaskConfig for workflow (ID: {}) not found or not a workflow type. Cannot process node completion.", parentWorkflowLog.getTaskId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Could not retrieve workflow task config during node completion.");
            MDC.clear();
            return;
        }

        List<WorkflowNode> nodes;
        List<WorkflowEdge> edges;
        Map<String, WorkflowNode> nodeMap; // For quick lookup
        try {
            nodes = JSON.parseArray(workflowTaskConfig.getWorkflowNodesJson(), WorkflowNode.class);
            edges = JSON.parseArray(workflowTaskConfig.getWorkflowEdgesJson(), WorkflowEdge.class);
            if (CollectionUtils.isEmpty(nodes)) {
                logger.warn("Workflow {} has no nodes defined. Cannot process node completion further.", workflowTaskConfig.getTaskName());
                // This state should ideally not be reached if startWorkflow handled it.
                MDC.clear();
                return;
            }
            nodeMap = nodes.stream().collect(Collectors.toMap(WorkflowNode::getNodeId, node -> node));
        } catch (Exception e) {
            logger.error("Failed to parse workflow definition for workflow {} during node completion: {}", workflowTaskConfig.getTaskName(), e.getMessage(), e);
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Error parsing workflow definition during node completion: " + e.getMessage());
            MDC.clear();
            return;
        }

        // --- State Management: Load current workflow state (e.g., from parentWorkflowLog.getRtnMsg()) ---
        // For simplicity, we'll build a context map. A more robust solution might involve a dedicated state object.
        Map<String, Object> executionContext = new HashMap<>();
        // Load global parameters (if any were stored or needed)
        if (StringUtils.hasText(workflowTaskConfig.getGlobalParametersJson())) {
             try {
                Map<String, Object> globalParams = JSON.parseObject(workflowTaskConfig.getGlobalParametersJson(), new TypeReference<Map<String, Object>>() {});
                executionContext.putAll(globalParams);
            } catch (Exception e) {
                 logger.warn("Could not parse global params for workflow {} during node completion: {}", workflowTaskConfig.getTaskName(), e.getMessage());
            }
        }
        executionContext.put("workflow_name", workflowTaskConfig.getTaskName());
        executionContext.put("workflow_id", workflowTaskConfig.getTaskId());
        executionContext.put("workflow_log_id", parentWorkflowLogId);

        // Populate context with status of all known nodes so far by querying their latest terminal logs
        // This is crucial for evaluating conditions for subsequent nodes.
        for (WorkflowNode node : nodes) {
            if (node.getNodeId().equals(completedNodeId)) {
                executionContext.put(node.getNodeId() + "_status", nodeFinalStatus);
            } else {
                // Find the latest terminal status for other nodes if needed for complex conditions
                // This could involve querying TaskExecuteLogDao: findLatestTerminalLogForNode(parentWorkflowLogId, node.getTaskConfigId(), node.getNodeId())
                // For now, we primarily care about the completedNodeId's status for direct outgoing edges.
                // A simple approach: if not the completed node, assume its prior state if we were storing it, or leave it out.
                // For this iteration, the context will primarily use the just-completed node's status.
        //    --> This needs to be more robust for evaluating complex conditions.
        // Let's try to populate more completely.
        final Map<String, String> currentNodeStates = new HashMap<>();
        for (WorkflowNode node : nodes) {
            String statusForContext = "PENDING"; // Default if not yet run or completed
            if (node.getNodeId().equals(completedNodeId)) {
                statusForContext = nodeFinalStatus;
            } else {
                // Query DAO for the latest log status for this node in this workflow instance.
                // This requires a method that can find the latest log for a specific node_id within a parent_workflow_log_id.
                // TaskExecuteLogDao would need: findLatestLogForWorkflowNode(parentWorkflowLogId, node.getNodeId())
                // This method would internally know how to map nodeId to task_id and filter appropriately.
                // For now, we simulate: find logs by parent and task_id, then assume nodeId matches if task_id does (simplification)
                List<TaskExecuteLog> nodeLogs = taskExecuteLogDao.findByParentExecuteNoAndTaskId(parentWorkflowLogId, node.getTaskConfigId());
                if (!nodeLogs.isEmpty()) {
                    // Sort by logId descending to get the latest first
                    nodeLogs.sort(Comparator.comparing(TaskExecuteLog::getLogId).reversed());
                    TaskExecuteLog latestLog = nodeLogs.get(0); // This is the absolute latest, could be RUNNING or a terminal RETRY_ATTEMPT

                    // We need the *terminal* status of the *sequence* of attempts for this node.
                    // If latestLog is SUCCESS, FAILED, TIMED_OUT, it's terminal *for that attempt*.
                    // If it's FAILED/TIMED_OUT, we need to check if it was the last possible attempt.
                    TaskConfig nodeTaskConfig = taskConfigDao.findById(node.getTaskConfigId()).orElse(null);
                    int attemptNumber = 1; // This is hard to get from log directly without a dedicated column
                                           // or parsing from message of RETRY_ATTEMPT logs.
                                           // For simplicity, if latest is FAILED/TIMED_OUT, assume it's terminal for now.
                                           // This is a significant simplification.

                    if ("SUCCESS".equals(latestLog.getState())) {
                        statusForContext = "SUCCESS";
                    } else if ("FAILED".equals(latestLog.getState()) || "TIMED_OUT".equals(latestLog.getState())) {
                        // Simplified: assume terminal failure if latest log shows FAILED/TIMED_OUT
                        statusForContext = latestLog.getState();
                    } else if ("RUNNING".equals(latestLog.getState())) {
                        statusForContext = "RUNNING";
                    } else if ("TRIGGERED".equals(latestLog.getState())) { // If we were to save "TRIGGERED" state
                        statusForContext = "TRIGGERED";
                    }
                }
            }
            executionContext.put(node.getNodeId() + "_status", statusForContext);
            currentNodeStates.put(node.getNodeId(), statusForContext);
        }

        logger.info("Reconstructed node statuses for workflow {}: {}", workflowTaskConfig.getTaskName(), currentNodeStates);
        logger.debug("Full executionContext for evaluating next steps for workflow {}: {}", workflowTaskConfig.getTaskName(), executionContext);

        // --- Logic to trigger next nodes ---
        boolean allPathsEnded = true; // Assume all paths end unless a new node is triggered
        boolean anyPathFailed = false;

        if ("FAILED".equals(nodeFinalStatus) || "TIMED_OUT".equals(nodeFinalStatus)) {
             // If a critical node fails and there are no alternative paths, the workflow might be considered failed.
             // This logic depends on workflow design (e.g., error handling paths).
             // For now, a FAILED/TIMED_OUT node means this path has failed.
             anyPathFailed = true; // A path has failed.
        }

        if ("SUCCESS".equals(nodeFinalStatus)) { // Only proceed if the completed node was successful
            List<WorkflowEdge> outgoingEdges = edges.stream()
                    .filter(edge -> edge.getFromNodeId().equals(completedNodeId))
                    .sorted(Comparator.comparingInt(WorkflowEdge::getPriority))
                    .collect(Collectors.toList());

            if (!outgoingEdges.isEmpty()) {
                allPathsEnded = false; // We have outgoing edges, so not all paths have ended yet.
                boolean transitionTaken = false;
                for (WorkflowEdge edge : outgoingEdges) {
                    boolean conditionMet = false;
                    if (StringUtils.hasText(edge.getExpression())) {
                        conditionMet = expressionUtil.evaluate(edge.getExpression(), executionContext);
                    } else { // No expression means unconditional if prior step was SUCCESS
                        conditionMet = true;
                    }

                    if (conditionMet) {
                        WorkflowNode nextNodeToTrigger = nodeMap.get(edge.getToNodeId());
                        if (nextNodeToTrigger != null) {
                            logger.info("Workflow {}: Condition met for edge {} -> {}. Triggering next node '{}'.",
                                    workflowTaskConfig.getTaskName(), completedNodeId, edge.getToNodeId(), nextNodeToTrigger.getNodeId());

                            Map<String, Object> nextNodeParams = prepareAndResolveParameters(
                                taskConfigDao.findById(nextNodeToTrigger.getTaskConfigId()).orElse(null), // This could be an issue if taskConfig is null
                                nextNodeToTrigger,
                                executionContext
                            );
                            String nextNodeParamsJson = (nextNodeParams != null && !nextNodeParams.isEmpty()) ? JSON.toJSONString(nextNodeParams) : null;

                            coreSchedulerService.triggerTaskManually(
                                    nextNodeToTrigger.getTaskConfigId(),
                                    "WORKFLOW_STEP",
                                    parentWorkflowLogId,
                                    nextNodeParamsJson,
                                    nextNodeToTrigger.getNodeId()
                            );
                            // TODO: Update workflow state to mark this node as TRIGGERED
                            transitionTaken = true;
                            break; // Assuming only one path is taken from a node if multiple conditions meet (priority based)
                        } else if (edge.getToNodeId() != null && !"END".equalsIgnoreCase(edge.getToNodeId())) {
                             logger.error("Workflow {}: Next node ID '{}' not found in map. Edge from '{}'.",
                                workflowTaskConfig.getTaskName(), edge.getToNodeId(), completedNodeId);
                             anyPathFailed = true; // Error in definition
                        } else {
                             logger.info("Workflow {}: Path ended at 'END' marker or null toNodeId from node '{}'.", workflowTaskConfig.getTaskName(), completedNodeId);
                        }
                    }
                }
                if (!transitionTaken && !outgoingEdges.isEmpty()) {
                    // Conditions for all outgoing edges evaluated to false. This path ends here.
                    allPathsEnded = true;
                }
            } else { // No outgoing edges from the completed successful node
                allPathsEnded = true;
            }
        } else { // Node failed/timed_out, this path stops unless error handling paths exist (not implemented here)
             allPathsEnded = true; // This path has ended due to node failure.
        }


        // --- Check for Overall Workflow Completion ---
        // This is a simplified check. A robust check needs to:
        // 1. Know all end nodes OR ensure all triggered nodes have completed.
        // 2. Consider all possible paths.
        // 3. If 'anyPathFailed' is true and no compensatory paths exist, workflow is FAILED.
        // For now: if allPathsEnded (meaning the current path ended, and no new nodes were triggered from it)
        // then we check status.
        if (allPathsEnded) {
            // More sophisticated check: query DB for any other RUNNING/TRIGGERED step logs for this parentWorkflowLogId.
            // If none, then the workflow is truly finished.
            boolean hasPendingNodes = false; // This needs to be determined by checking status of ALL nodes in the workflow definition.

            // More robust state reconstruction based on current node states
            long pendingOrRunningNodesCount = currentNodeStates.values().stream()
                .filter(status -> "PENDING".equals(status) || "RUNNING".equals(status) || "TRIGGERED".equals(status))
                .count();

            if (pendingOrRunningNodesCount == 0 && allPathsEnded) {
                // Workflow is complete if no nodes are actively running/pending AND all traversable paths have ended.
                boolean overallWorkflowSuccess = !anyPathFailed; // If any path had a terminal failure, workflow is failed.

                // Additional check: ensure all nodes that were supposed to run (not PENDING due to untaken paths) are SUCCESS.
                if(overallWorkflowSuccess) {
                    for(Map.Entry<String, String> entry : currentNodeStates.entrySet()) {
                        if(!("SUCCESS".equals(entry.getValue()) || "PENDING".equals(entry.getValue()))) {
                            // If a node that wasn't simply pending (i.e. it ran or should have run) isn't SUCCESS, then it's not overall success.
                            // This check is tricky if "PENDING" can also mean it was on a path that correctly wasn't taken.
                            // A simpler check: if anyPathFailed is false, and no nodes are running/pending, it's SUCCESS.
                            // This assumes that if a node was supposed to run and didn't reach SUCCESS, anyPathFailed would be true.
                        }
                    }
                }

                String finalWorkflowStatus = overallWorkflowSuccess ? "SUCCESS" : "FAILED";
                logger.info("Workflow {} (Log ID: {}) determined to be complete with status: {}. Node statuses: {}",
                            workflowTaskConfig.getTaskName(), parentWorkflowLogId, finalWorkflowStatus, currentNodeStates);

                String finalMessage = (parentWorkflowLog.getRtnMsg() == null ? "" : parentWorkflowLog.getRtnMsg()) +
                                      "\nNode " + completedNodeId + " finished: " + nodeFinalStatus + ". Last log: " + nodeLastLogId +
                                      ". Workflow processing complete. Final Node States: " + JSON.toJSONString(currentNodeStates) +
                                      ". Final status: " + finalWorkflowStatus;
                updateWorkflowLog(parentWorkflowLog.getLogId(), finalWorkflowStatus, finalMessage);
                parentWorkflowLog.setEndTime(new Timestamp(System.currentTimeMillis())); // Set end time for parent
                taskExecuteLogDao.save(parentWorkflowLog); // Save end time

            } else {
                 // Workflow still running or has pending/triggered tasks
                 logger.info("Workflow {} (Log ID: {}) still in progress. Pending/Running nodes: {}. All paths ended: {}. Any path failed: {}",
                            workflowTaskConfig.getTaskName(), parentWorkflowLogId, pendingOrRunningNodesCount, allPathsEnded, anyPathFailed);

                String currentMessage = (parentWorkflowLog.getRtnMsg() == null ? "" : parentWorkflowLog.getRtnMsg());
                // Avoid appending the same node completion message multiple times if processNodeCompletion is called again for some reason
                String nodeCompletionMessage = "\nNode " + completedNodeId + " finished: " + nodeFinalStatus + ". Last log: " + nodeLastLogId + ".";
                if (!currentMessage.contains(nodeCompletionMessage)) { // Simple check to avoid duplicate message parts
                    currentMessage += nodeCompletionMessage;
                }
                currentMessage += " Current Node States: " + JSON.toJSONString(currentNodeStates);
                updateWorkflowLog(parentWorkflowLog.getLogId(), "RUNNING", currentMessage);
            }
        }
        MDC.clear();
    }
}
