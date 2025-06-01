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
        StringBuilder overallWorkflowMessage = new StringBuilder("Workflow started. Global params loaded: " + initialContextData.keySet() + "\n");
        Map<String, Object> executionContext = new HashMap<>(initialContextData);

        boolean overallSuccess = executeNodeRecursive(startNode, parentWorkflowLog, nodeMap, edges, executionContext, overallWorkflowMessage);

        if (overallSuccess) {
            logger.info("Workflow {} (ID: {}, Log ID: {}) completed successfully.",
                    workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId(), parentWorkflowLog.getLogId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow completed successfully. " + overallWorkflowMessage.toString());
        } else {
            logger.warn("Workflow {} (ID: {}, Log ID: {}) failed or stopped.",
                    workflowTaskConfig.getTaskName(), workflowTaskConfig.getTaskId(), parentWorkflowLog.getLogId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Workflow failed or stopped. " + overallWorkflowMessage.toString());
        }
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
                .orElse(nodes.get(0)); // Fallback to the first node
    }

    /**
     * Recursively executes workflow nodes based on defined edges and conditions.
     *
     * @param currentNode The current {@link WorkflowNode} to execute.
     * @param parentWorkflowLog The main log entry for the parent workflow.
     * @param nodeMap A map of all nodes in the workflow, keyed by their ID.
     * @param allEdges A list of all edges defining transitions in the workflow.
     * @param contextData A map holding the execution context (global params, node statuses, outputs).
     * @param overallWorkflowMessage A {@link StringBuilder} to accumulate messages about the workflow's progress.
     * @return {@code true} if the current execution path completed successfully, {@code false} otherwise.
     */
    private boolean executeNodeRecursive(WorkflowNode currentNode, TaskExecuteLog parentWorkflowLog,
                                         Map<String, WorkflowNode> nodeMap, List<WorkflowEdge> allEdges,
                                         Map<String, Object> contextData, StringBuilder overallWorkflowMessage) {
        if (currentNode == null) {
            overallWorkflowMessage.append("Reached end of a workflow path (current node is null).\n");
            return true; // Successfully completed this path
        }

        logger.info("Executing workflow node: Node ID '{}', TaskConfigId: {} (Parent Workflow Log ID: {}). Context keys: {}",
                currentNode.getNodeId(), currentNode.getTaskConfigId(), parentWorkflowLog.getLogId(), contextData.keySet());
        overallWorkflowMessage.append("Executing node '").append(currentNode.getNodeId()).append("': ");

        Optional<TaskConfig> referencedTaskConfigOpt = taskConfigDao.findById(currentNode.getTaskConfigId());
        if (!referencedTaskConfigOpt.isPresent() || referencedTaskConfigOpt.get().getTaskType() != 0) { // Must be BEAN task
            String errorMsg = String.format("Node '%s' references invalid or non-BEAN TaskConfigId: %d",
                    currentNode.getNodeId(), currentNode.getTaskConfigId());
            logger.error(errorMsg);
            overallWorkflowMessage.append("Failed - ").append(errorMsg).append(".\n");
            contextData.put(currentNode.getNodeId() + "_status", "ERROR_INVALID_CONFIG");
            return false; // Node execution failed due to bad configuration
        }
        TaskConfig referencedTaskConfig = referencedTaskConfigOpt.get();

        TaskExecuteLog stepLog = new TaskExecuteLog();
        stepLog.setTaskId(referencedTaskConfig.getTaskId());
        stepLog.setStartTime(new Timestamp(System.currentTimeMillis()));
        stepLog.setState("RUNNING");
        stepLog.setInstanceId(distributedLockService.getSchedulerInstanceId());
        stepLog.setParentLogId(parentWorkflowLog.getLogId());
        stepLog.setTaskPattern("WORKFLOW_STEP");
        TaskExecuteLog savedStepLog = taskExecuteLogDao.save(stepLog);

        String stepExecuteNo = String.valueOf(savedStepLog.getLogId());
        MDC.put("execute_no", "$"  + stepExecuteNo + "$"); // Add step's execute_no to MDC
        MDC.put("uuid", "$"  + stepExecuteNo + "$");

        String stepStatus;
        String stepMessage = null;

        try {
            Map<String, Object> resolvedParameters = prepareAndResolveParameters(referencedTaskConfig, currentNode, contextData);

            TaskConfig effectiveTaskConfigForBean = new TaskConfig(); // Temporary TaskConfig for this specific execution
            BeanUtils.copyProperties(referencedTaskConfig, effectiveTaskConfigForBean); // Start with base config
            if (resolvedParameters != null && !resolvedParameters.isEmpty()) {
                // Override beanParameters with resolved ones for this execution
                effectiveTaskConfigForBean.setBeanParameters(JSON.toJSONString(resolvedParameters));
            }

            beanTaskExecutor.execute(effectiveTaskConfigForBean); // This may throw exceptions including TaskTimeoutException
            stepStatus = "SUCCESS";
            overallWorkflowMessage.append("Succeeded. ");
        } catch (BeanTaskExecutor.TaskTimeoutException e) {
            logger.warn("Node '{}' (Task ID: {}) timed out: {}", currentNode.getNodeId(), referencedTaskConfig.getTaskId(), e.getMessage());
            stepStatus = "TIMED_OUT";
            stepMessage = e.getMessage();
            overallWorkflowMessage.append("Timed Out. ");
        } catch (Exception e) {
            logger.error("Node '{}' (Task ID: {}) failed execution: {}", currentNode.getNodeId(), referencedTaskConfig.getTaskId(), e.getMessage(), e);
            stepStatus = "FAILED";
            stepMessage = e.getMessage();
            overallWorkflowMessage.append("Failed. ");
        }

        taskExecuteLogDao.updateLogStatus(savedStepLog.getLogId(), stepStatus, stepMessage);
        contextData.put(currentNode.getNodeId() + "_status", stepStatus);
        // Future enhancement: capture actual output from beanTaskExecutor.execute (if it returns a value)
        // and put it into contextData, e.g., contextData.put(currentNode.getNodeId() + "_output", actualOutput);

        // Determine next node based on edges and conditions
        List<WorkflowEdge> outgoingEdges = allEdges.stream()
                .filter(edge -> edge.getFromNodeId().equals(currentNode.getNodeId()))
                .sorted(Comparator.comparingInt(WorkflowEdge::getPriority)) // Lower number = higher priority
                .collect(Collectors.toList());

        if (outgoingEdges.isEmpty()) {
            overallWorkflowMessage.append("No outgoing edges from node '").append(currentNode.getNodeId()).append("'. Path ends.\n");
            // A path ending is not necessarily a workflow failure if the step itself was not a failure.
            return "SUCCESS".equals(stepStatus) || "TIMED_OUT".equals(stepStatus);
        }

        for (WorkflowEdge edge : outgoingEdges) {
            boolean conditionMet = true; // Default to true if no condition is defined
            String evaluatedExpression = StringUtils.hasText(edge.getExpression()) ? edge.getExpression() : edge.getCondition();
            if (StringUtils.hasText(evaluatedExpression)) {
                conditionMet = expressionUtil.evaluate(evaluatedExpression, contextData);
                logger.debug("Edge from '{}' to '{}': expression/condition '{}' (stepStatus='{}') evaluated to {}",
                    edge.getFromNodeId(), edge.getToNodeId(), evaluatedExpression, stepStatus, conditionMet);
            } else {
                // No expression and no simple condition implies unconditional transition if current step was SUCCESSFUL
                conditionMet = "SUCCESS".equals(stepStatus);
                 logger.debug("Edge from '{}' to '{}': no expression/condition, defaulting based on stepStatus='{}', conditionMet={}",
                    edge.getFromNodeId(), edge.getToNodeId(), stepStatus, conditionMet);
            }

            if (conditionMet) {
                overallWorkflowMessage.append("Transitioning via edge from '").append(edge.getFromNodeId())
                                      .append("' to '").append(edge.getToNodeId())
                                      .append("' due to expression/condition '").append(evaluatedExpression).append("'.\n");
                WorkflowNode nextNode = nodeMap.get(edge.getToNodeId());
                if (nextNode == null && edge.getToNodeId() != null && !"END".equalsIgnoreCase(edge.getToNodeId())) {
                    logger.error("Next node ID '{}' defined in edge from '{}' not found in node map. Workflow terminates.",
                            edge.getToNodeId(), edge.getFromNodeId());
                    overallWorkflowMessage.append("Error: Next node '").append(edge.getToNodeId()).append("' not found.\n");
                    return false; // Critical error in workflow definition
                }
                // Recursively execute the next node. If it fails, the whole workflow is marked as failed.
                return executeNodeRecursive(nextNode, parentWorkflowLog, nodeMap, allEdges, contextData, overallWorkflowMessage);
            }
        }

        overallWorkflowMessage.append("No outgoing edge conditions met for node '").append(currentNode.getNodeId()).append("'. Workflow path ends.\n");
        // If no conditions met, this path of the workflow ends.
        // This is considered a successful completion of this path if the current node itself didn't fail.
        return "SUCCESS".equals(stepStatus) || "TIMED_OUT".equals(stepStatus);
    }

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
        taskExecuteLogDao.updateLogStatus(logId, status, finalMessage);
    }
}
