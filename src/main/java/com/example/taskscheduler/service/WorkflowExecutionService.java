package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.util.ExpressionUtil; // Import the new utility
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class WorkflowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionService.class);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TaskConfigDao taskConfigDao;
    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    @Autowired
    private BeanTaskExecutor beanTaskExecutor;
    @Autowired
    private DistributedLockService distributedLockService;
    @Autowired
    private ExpressionUtil expressionUtil; // Autowire ExpressionUtil

    public void startWorkflow(TaskConfig workflowTaskConfig, TaskExecuteLog parentWorkflowLog) {
        // Assuming task_type=3 is WORKFLOW. This check might be better done in CoreSchedulerService
        if (workflowTaskConfig == null || workflowTaskConfig.getTaskType() != 3) {
            logger.error("Task {} is not a workflow task or is null.", workflowTaskConfig != null ? workflowTaskConfig.getTaskName() : "null");
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Invalid workflow task configuration (not type 3 or null).");
            return;
        }
        logger.info("Starting workflow: {} (Log ID: {})", workflowTaskConfig.getTaskName(), parentWorkflowLog.getLogId());

        List<WorkflowNode> nodes;
        List<WorkflowEdge> edges;
        Map<String, Object> initialContextData = new HashMap<>();

        try {
            // Parse global parameters
            if (StringUtils.hasText(workflowTaskConfig.getGlobalParametersJson())) {
                Map<String, Object> globalParams = objectMapper.readValue(workflowTaskConfig.getGlobalParametersJson(), new TypeReference<Map<String, Object>>() {});
                initialContextData.putAll(globalParams);
                logger.info("Loaded global parameters for workflow {}: {}", workflowTaskConfig.getTaskName(), globalParams.keySet());
            }
            initialContextData.put("workflow_name", workflowTaskConfig.getTaskName());
            initialContextData.put("workflow_id", workflowTaskConfig.getTaskId());


            if (!StringUtils.hasText(workflowTaskConfig.getWorkflowNodesJson())) {
                logger.warn("Workflow {} has no nodes defined.", workflowTaskConfig.getTaskName());
                updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow has no nodes to execute.");
                return;
            }
            nodes = objectMapper.readValue(workflowTaskConfig.getWorkflowNodesJson(), new TypeReference<List<WorkflowNode>>() {});
            
            // Edges are now critical
            if (!StringUtils.hasText(workflowTaskConfig.getWorkflowEdgesJson())) {
                logger.warn("Workflow {} has no edges defined. Cannot determine execution flow.", workflowTaskConfig.getTaskName());
                updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Workflow definition incomplete: missing edges.");
                return;
            }
            edges = objectMapper.readValue(workflowTaskConfig.getWorkflowEdgesJson(), new TypeReference<List<WorkflowEdge>>() {});

        } catch (Exception e) {
            logger.error("Failed to parse workflow definition for workflow {}: {}", workflowTaskConfig.getTaskName(), e.getMessage(), e);
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Error parsing workflow definition: " + e.getMessage());
            return;
        }

        if (CollectionUtils.isEmpty(nodes)) {
            logger.info("Workflow {} has no nodes to execute.", workflowTaskConfig.getTaskName());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow executed successfully (no nodes).");
            return;
        }

        // Determine start node (e.g., node with no incoming edges, or explicitly marked)
        // For now, still assume first node in the list is the start node for simplicity in Phase 2,
        // or a node named "start" or similar conventional ID.
        // A more robust way: find nodes that are not targets in any edge.
        WorkflowNode startNode = findStartNode(nodes, edges);
        if (startNode == null) {
            logger.error("Could not determine start node for workflow {}.", workflowTaskConfig.getTaskName());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Could not determine start node.");
            return;
        }
        logger.info("Determined start node for workflow {}: {}", workflowTaskConfig.getTaskName(), startNode.getNodeId());


        Map<String, WorkflowNode> nodeMap = nodes.stream().collect(Collectors.toMap(WorkflowNode::getNodeId, node -> node));
        
        StringBuilder overallWorkflowMessage = new StringBuilder("Workflow started with global params: " + initialContextData.keySet() + "\n");
        
        // Initialize contextData with global parameters
        Map<String, Object> executionContext = new HashMap<>(initialContextData);

        boolean overallSuccess = executeNodeRecursive(startNode, parentWorkflowConfig, parentWorkflowLog, nodeMap, edges, executionContext, overallWorkflowMessage);

        if (overallSuccess) {
            logger.info("Workflow {} (Log ID: {}) completed successfully.", workflowTaskConfig.getTaskName(), parentWorkflowLog.getLogId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "SUCCESS", "Workflow completed successfully. " + overallWorkflowMessage.toString());
        } else {
            logger.warn("Workflow {} (Log ID: {}) failed or stopped.", workflowTaskConfig.getTaskName(), parentWorkflowLog.getLogId());
            updateWorkflowLog(parentWorkflowLog.getLogId(), "FAILED", "Workflow failed or stopped. " + overallWorkflowMessage.toString());
        }
    }

    private WorkflowNode findStartNode(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        if (nodes.isEmpty()) return null;
        // A start node is one that is not a target in any edge.
        // If multiple such nodes exist, this simple logic might pick one arbitrarily or need refinement.
        // Or, a node explicitly named "start".
        Optional<WorkflowNode> predesignatedStartNode = nodes.stream().filter(n -> "start".equalsIgnoreCase(n.getNodeId())).findFirst();
        if(predesignatedStartNode.isPresent()) return predesignatedStartNode.get();

        List<String> targetNodeIds = edges.stream().map(WorkflowEdge::getToNodeId).collect(Collectors.toList());
        return nodes.stream()
                .filter(node -> !targetNodeIds.contains(node.getNodeId()))
                .findFirst()
                .orElse(nodes.get(0)); // Fallback to first node if all nodes are targeted (e.g. circular start)
    }


    private boolean executeNodeRecursive(WorkflowNode currentNode, TaskConfig parentWorkflowConfig, TaskExecuteLog parentWorkflowLog,
                                         Map<String, WorkflowNode> nodeMap, List<WorkflowEdge> allEdges,
                                         Map<String, Object> contextData, StringBuilder overallWorkflowMessage) {
        if (currentNode == null) {
            overallWorkflowMessage.append("Reached end of path (null node).\n");
            return true; // Successfully completed this path
        }

        logger.info("Executing workflow node: {} (TaskConfigId: {}) for parent log ID: {}. Context keys: {}",
                currentNode.getNodeId(), currentNode.getTaskConfigId(), parentWorkflowLog.getLogId(), contextData.keySet());
        overallWorkflowMessage.append("Executing node '").append(currentNode.getNodeId()).append("': ");

        Optional<TaskConfig> referencedTaskConfigOpt = taskConfigDao.findById(currentNode.getTaskConfigId());
        if (!referencedTaskConfigOpt.isPresent() || referencedTaskConfigOpt.get().getTaskType() != 0) { // Must be a BEAN task
            logger.error("Node {} references invalid or non-bean TaskConfigId: {}", currentNode.getNodeId(), currentNode.getTaskConfigId());
            overallWorkflowMessage.append("Failed - Invalid referenced task.\n");
            contextData.put(currentNode.getNodeId() + "_status", "ERROR_INVALID_CONFIG");
            return false;
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

        String stepStatus;
        String stepMessage = null;

        try {
            Map<String, Object> resolvedParameters = prepareAndResolveParameters(referencedTaskConfig, currentNode, contextData);
            
            TaskConfig effectiveTaskConfigForBean = new TaskConfig();
            BeanUtils.copyProperties(referencedTaskConfig, effectiveTaskConfigForBean);
            if (resolvedParameters != null && !resolvedParameters.isEmpty()) {
                effectiveTaskConfigForBean.setBeanParameters(objectMapper.writeValueAsString(resolvedParameters));
            }
            
            beanTaskExecutor.execute(effectiveTaskConfigForBean);
            stepStatus = "SUCCESS";
            overallWorkflowMessage.append("Succeeded. ");
        } catch (BeanTaskExecutor.TaskTimeoutException e) {
            logger.warn("Node {} (Task ID: {}) timed out.", currentNode.getNodeId(), referencedTaskConfig.getTaskId(), e);
            stepStatus = "TIMED_OUT";
            stepMessage = e.getMessage();
            overallWorkflowMessage.append("Timed Out. ");
        } catch (Exception e) {
            logger.error("Node {} (Task ID: {}) failed execution.", currentNode.getNodeId(), referencedTaskConfig.getTaskId(), e);
            stepStatus = "FAILED";
            stepMessage = e.getMessage();
            overallWorkflowMessage.append("Failed. ");
        }
        
        taskExecuteLogDao.updateLogStatus(savedStepLog.getLogId(), stepStatus, stepMessage);
        contextData.put(currentNode.getNodeId() + "_status", stepStatus);
        // Placeholder for actual output, if BeanTaskExecutor could return it
        // contextData.put(currentNode.getNodeId() + "_output", Map.of("status", stepStatus, "message", stepMessage == null ? "" : stepMessage));


        // Determine next node based on edges and conditions
        List<WorkflowEdge> outgoingEdges = allEdges.stream()
                .filter(edge -> edge.getFromNodeId().equals(currentNode.getNodeId()))
                .sorted(Comparator.comparingInt(WorkflowEdge::getPriority)) // Lower number = higher priority
                .collect(Collectors.toList());

        if (outgoingEdges.isEmpty()) {
            overallWorkflowMessage.append("No outgoing edges from node '").append(currentNode.getNodeId()).append("'. Path ends.\n");
            return "SUCCESS".equals(stepStatus) || "TIMED_OUT".equals(stepStatus); // Path ends. Success if step itself didn't fail critically for workflow progression.
                                                                                   // Or, based on if it's an "end node".
        }

        for (WorkflowEdge edge : outgoingEdges) {
            boolean conditionMet = false;
            if (StringUtils.hasText(edge.getExpression())) {
                conditionMet = expressionUtil.evaluate(edge.getExpression(), contextData);
                logger.debug("Edge from {} to {}: expression '{}' evaluated to {}", edge.getFromNodeId(), edge.getToNodeId(), edge.getExpression(), conditionMet);
            } else if (StringUtils.hasText(edge.getCondition())) { // Fallback to simple condition if expression is empty
                 // Legacy/Simple condition: "SUCCESS", "FAILURE"
                 conditionMet = ("SUCCESS".equalsIgnoreCase(edge.getCondition()) && "SUCCESS".equals(stepStatus)) ||
                                ("FAILURE".equalsIgnoreCase(edge.getCondition()) && ("FAILED".equals(stepStatus) || "TIMED_OUT".equals(stepStatus)));
                 logger.debug("Edge from {} to {}: simple condition '{}' (stepStatus='{}') evaluated to {}", edge.getFromNodeId(), edge.getToNodeId(), edge.getCondition(), stepStatus, conditionMet);
            } else {
                // No expression and no simple condition often means an unconditional SUCCESS path
                conditionMet = "SUCCESS".equals(stepStatus);
                 logger.debug("Edge from {} to {}: no expression/condition, defaulting to conditionMet={} based on stepStatus='{}'", edge.getFromNodeId(), edge.getToNodeId(), conditionMet, stepStatus);
            }

            if (conditionMet) {
                overallWorkflowMessage.append("Transitioning via edge to '").append(edge.getToNodeId()).append("' due to expression/condition '").append(edge.getExpression() != null ? edge.getExpression() : edge.getCondition()).append("'.\n");
                WorkflowNode nextNode = nodeMap.get(edge.getToNodeId());
                if (nextNode == null && edge.getToNodeId() != null && !"END".equalsIgnoreCase(edge.getToNodeId())) { // "END" can be a virtual target
                    logger.error("Next node ID '{}' defined in edge from '{}' not found in node map.", edge.getToNodeId(), edge.getFromNodeId());
                    overallWorkflowMessage.append("Error: Next node '").append(edge.getToNodeId()).append("' not found.\n");
                    return false; // Critical error in workflow definition
                }
                return executeNodeRecursive(nextNode, parentWorkflowConfig, parentWorkflowLog, nodeMap, allEdges, contextData, overallWorkflowMessage);
            }
        }
        
        overallWorkflowMessage.append("No outgoing edge conditions met for node '").append(currentNode.getNodeId()).append("'. Workflow path ends.\n");
        // If no conditions met, it means this path of the workflow ends.
        // Whether this is an overall success or failure might depend on workflow design.
        // For now, if a step itself didn't fail, and it just has no valid outgoing path, it's not necessarily a workflow failure.
        return "SUCCESS".equals(stepStatus) || "TIMED_OUT".equals(stepStatus); // Path ends.
    }
    
    private Map<String, Object> prepareAndResolveParameters(TaskConfig referencedTaskConfig, WorkflowNode node, Map<String, Object> contextData) {
        Map<String, Object> baseParams = new HashMap<>();
        try {
            if (StringUtils.hasText(referencedTaskConfig.getBeanParameters())) {
                // Base parameters from the referenced task config might also contain templates
                String resolvedBaseParamsJson = expressionUtil.resolveTemplates(referencedTaskConfig.getBeanParameters(), contextData);
                baseParams = objectMapper.readValue(resolvedBaseParamsJson, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            logger.warn("Error parsing or resolving base parameters for task {}: {}", referencedTaskConfig.getTaskName(), e.getMessage(), e);
        }

        Map<String, Object> nodeParams = new HashMap<>();
        if (node.getParameters() != null && !node.getParameters().isEmpty()) {
            node.getParameters().forEach((key, value) -> {
                if (value instanceof String) {
                    // Resolve templates in node-specific parameters
                    nodeParams.put(key, expressionUtil.resolveTemplates((String) value, contextData));
                } else {
                    nodeParams.put(key, value); // Keep non-string values as is
                }
            });
        }
        
        // Merge: Node parameters override base parameters, which might have been resolved from context
        Map<String, Object> mergedParams = new HashMap<>(baseParams);
        mergedParams.putAll(nodeParams); // Node-specific (and resolved) parameters take precedence
        
        logger.debug("Resolved parameters for node {}: {}", node.getNodeId(), mergedParams);
        return mergedParams;
    }

    private void updateWorkflowLog(Integer logId, String status, String message) {
        // Ensure message is not too long for ex_msg column
        String finalMessage = message;
        if (message != null && message.length() > 2000) { // Assuming ex_msg is TEXT but good to cap for summary
            finalMessage = message.substring(0, 1997) + "...";
        }
        taskExecuteLogDao.updateLogStatus(logId, status, finalMessage);
    }
}
