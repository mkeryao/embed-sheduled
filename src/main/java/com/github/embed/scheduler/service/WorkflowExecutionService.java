package com.github.embed.scheduler.service;

import com.alibaba.fastjson.JSON;
import com.github.embed.scheduler.dao.TaskConfigDao;
import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.dao.TaskWorkflowInstanceDao;
import com.github.embed.scheduler.dto.workflow.WorkflowEdge;
import com.github.embed.scheduler.dto.workflow.WorkflowNode;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.entity.WorkflowInstance;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import com.github.embed.scheduler.scheduler.CoreSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkflowExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowExecutionService.class);

    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private TaskWorkflowInstanceDao taskWorkflowInstanceDao;


    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private WorkflowAsyncExecutor asyncExecutor;

    @Autowired
    private CoreSchedulerService coreSchedulerService;

    @Transactional
    public void startWorkflow(int workflowId, Long parentLogId) {
        logger.info("Attempting to start workflow with ID: {} for parent log ID: {}", workflowId, parentLogId);

        // 1. Use the provided parent log ID. Do not create a new one.
        TaskExecuteLog parentWorkflowLog = taskExecuteLogDao.findById(parentLogId)
                .orElseThrow(() -> new IllegalArgumentException("Parent log with ID " + parentLogId + " not found."));
        
        // Ensure the parent log is marked as a workflow parent and is running
        parentWorkflowLog.setTaskPattern(ExecutionPattern.WORKFLOW_PARENT.name());
        parentWorkflowLog.setState(ExecutionState.RUNNING.name());
        taskExecuteLogDao.update(parentWorkflowLog);
        logger.info("Reusing parent workflow log with ID: {}", parentLogId);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowId(workflowId);
        instance.setStatus(ExecutionState.RUNNING.name());
        int instanceId = taskWorkflowInstanceDao.create(instance);
        logger.info("Created workflow instance with ID: {}", instanceId);

        // Associate parent log with instanceId
        parentWorkflowLog.setInstanceId(String.valueOf(instanceId));
        taskExecuteLogDao.update(parentWorkflowLog);


        TaskConfig workflowConfig = taskConfigDao.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow with ID " + workflowId + " not found."));

        if (!StringUtils.hasText(workflowConfig.getWorkflowNodesJson())) {
            logger.warn("Workflow {} has no nodes defined. Completing immediately.", workflowId);
            instance.setStatus(ExecutionState.COMPLETED.name());
            taskWorkflowInstanceDao.update(instance);
            parentWorkflowLog.setState(ExecutionState.SUCCESS.name());
            parentWorkflowLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
            taskExecuteLogDao.update(parentWorkflowLog);
            return;
        }

        List<WorkflowNode> nodes = JSON.parseArray(workflowConfig.getWorkflowNodesJson(), WorkflowNode.class);
        List<WorkflowEdge> edges = StringUtils.hasText(workflowConfig.getWorkflowEdgesJson())
                ? JSON.parseArray(workflowConfig.getWorkflowEdgesJson(), WorkflowEdge.class)
                : java.util.Collections.emptyList();

        List<WorkflowNode> startNodes = findStartNodes(nodes, edges);
        logger.info("Found {} start nodes for workflow ID: {}", startNodes.size(), workflowId);

        if (startNodes.isEmpty() && !nodes.isEmpty()) {
             logger.warn("Workflow {} has nodes but no start nodes (potential cycle). Cannot start.", workflowId);
             instance.setStatus(ExecutionState.FAILED.name());
             instance.setRtnMsg("Workflow has no start nodes.");
             taskWorkflowInstanceDao.update(instance);
             parentWorkflowLog.setState(ExecutionState.FAILED.name());
             parentWorkflowLog.setRtnMsg("Workflow has no start nodes.");
             parentWorkflowLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
             taskExecuteLogDao.update(parentWorkflowLog);
             return;
        }

        for (WorkflowNode startNode : startNodes) {
            // Pass the parent workflow log ID to the execution of the first nodes
            executeStartNode(startNode, workflowId, instanceId, parentLogId);
        }
    }

    /**
     * Creates the initial log for a start node and then calls the main execution logic.
     * This is the entry point for nodes that don't have dependencies.
     */
    @Transactional
    public void executeStartNode(WorkflowNode node, int workflowId, int workflowInstanceId, Long parentWorkflowLogId) {
        TaskExecuteLog nodeLog = new TaskExecuteLog();
        nodeLog.setTaskId(node.getTaskConfigId());
        nodeLog.setWorkflowId(workflowId);
        nodeLog.setWorkflowInstanceId(workflowInstanceId);
        nodeLog.setWorkflowNodeId(node.getNodeId());
        // State is initially PENDING. The executeNode method will change it to RUNNING.
        nodeLog.setState(ExecutionState.PENDING.name()); 
        nodeLog.setTaskPattern(ExecutionPattern.WORKFLOW_STEP.name());
        nodeLog.setParentLogId(parentWorkflowLogId != null ? parentWorkflowLogId.intValue() : null);
        nodeLog.setInstanceId(String.valueOf(workflowInstanceId));
        
        // The save is now part of executeNode's transaction
        executeNode(nodeLog);
    }

    @Transactional
    public void processNodeCompletion(long completedLogId) {
        TaskExecuteLog completedLog = taskExecuteLogDao.findById(completedLogId)
                .orElseThrow(() -> new IllegalStateException("Completed log with ID " + completedLogId + " not found."));

        if (!ExecutionState.SUCCESS.name().equals(completedLog.getState())) {
            logger.warn("Node with LogId {} did not complete successfully (state: {}). Halting this path.", completedLogId, completedLog.getState());
            // Mark workflow as failed
            WorkflowInstance instance = taskWorkflowInstanceDao.findById(completedLog.getWorkflowInstanceId()).orElse(null);
            if(instance != null && !ExecutionState.FAILED.name().equals(instance.getStatus())) {
                instance.setStatus(ExecutionState.FAILED.name());
                instance.setRtnMsg("Node " + completedLog.getWorkflowNodeId() + " failed.");
                taskWorkflowInstanceDao.update(instance);
            }
            return;
        }

        int workflowId = completedLog.getWorkflowId();
        int instanceId = completedLog.getWorkflowInstanceId();
        String completedNodeId = completedLog.getWorkflowNodeId();
        Long parentWorkflowLogId = completedLog.getParentLogId() != null ? completedLog.getParentLogId().longValue() : null;

        if (parentWorkflowLogId == null) {
            logger.warn("Completed log {} is not part of a workflow (parent_execute_no is null). Aborting completion processing.", completedLogId);
            return;
        }

        TaskConfig workflowConfig = taskConfigDao.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow with ID " + workflowId + " not found."));

        List<WorkflowNode> nodes = JSON.parseArray(workflowConfig.getWorkflowNodesJson(), WorkflowNode.class);
        List<WorkflowEdge> edges = JSON.parseArray(workflowConfig.getWorkflowEdgesJson(), WorkflowEdge.class);
        Map<String, WorkflowNode> nodeMap = nodes.stream().collect(Collectors.toMap(WorkflowNode::getNodeId, node -> node));

        List<WorkflowEdge> outgoingEdges = edges.stream()
                .filter(edge -> edge.getFromNodeId().equals(completedNodeId))
                .collect(Collectors.toList());

        logger.info("Processing completion of node '{}'. Found {} outgoing edges.", completedNodeId, outgoingEdges.size());

        for (WorkflowEdge edge : outgoingEdges) {
            WorkflowNode downstreamNode = nodeMap.get(edge.getToNodeId());
            if (downstreamNode != null && isReadyToRun(downstreamNode, instanceId, edges)) {
                logger.info("Downstream node '{}' is ready to run. Triggering async lock acquisition.", downstreamNode.getNodeId());
                // Instead of executing directly, attempt to acquire a lock and execute asynchronously
                asyncExecutor.tryAcquireLockAndExecuteNode(downstreamNode, workflowId, instanceId, parentWorkflowLogId);
            } else {
                if (downstreamNode != null) {
                    logger.info("Downstream node '{}' is not yet ready to run. Waiting for other dependencies.", downstreamNode.getNodeId());
                }
            }
        }

        if (isWorkflowComplete(instanceId, nodes, edges)) {
            logger.info("Workflow instance {} is complete. Updating status.", instanceId);
            markWorkflowAsComplete(instanceId, parentWorkflowLogId);
        }
    }

    /**
     * Marks a workflow instance and its parent log as failed.
     */
    @Transactional
    public void markWorkflowAsFailed(int instanceId, String reason) {
        WorkflowInstance instance = taskWorkflowInstanceDao.findById(instanceId).orElse(null);
        if (instance != null && !ExecutionState.FAILED.name().equals(instance.getStatus())) {
            instance.setStatus(ExecutionState.FAILED.name());
            instance.setRtnMsg(reason);
            taskWorkflowInstanceDao.update(instance);
            logger.warn("Marked workflow instance {} as FAILED. Reason: {}", instanceId, reason);
        }
    }

    /**
     * Marks a workflow instance and its parent log as complete.
     */
    @Transactional
    public void markWorkflowAsComplete(int instanceId, Long parentWorkflowLogId) {
        WorkflowInstance instance = taskWorkflowInstanceDao.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Workflow instance " + instanceId + " not found."));
        
        if (!ExecutionState.COMPLETED.name().equals(instance.getStatus())) {
            instance.setStatus(ExecutionState.COMPLETED.name());
            taskWorkflowInstanceDao.update(instance);

            if (parentWorkflowLogId != null) {
                TaskExecuteLog parentLog = taskExecuteLogDao.findById(parentWorkflowLogId)
                    .orElseThrow(() -> new IllegalStateException("Parent workflow log " + parentWorkflowLogId + " not found."));
                parentLog.setState(ExecutionState.SUCCESS.name());
                parentLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                taskExecuteLogDao.update(parentLog);
            }
            logger.info("Marked workflow instance {} as COMPLETED.", instanceId);
        }
    }

    /**
     * Executes the logic for a given node, identified by its log entry.
     * This method is transactional and will attempt to save the PENDING log first.
     * If the save fails due to a duplicate key, it will throw a DuplicateKeyException,
     * which is caught by the async caller.
     * @param nodeLog The log entry for the node to execute, expected to be in PENDING state.
     */
    @Transactional
    public void executeNode(TaskExecuteLog nodeLog) {
        // 1. Save the initial PENDING log entry to get an ID
        TaskExecuteLog savedLog = taskExecuteLogDao.save(nodeLog);
        long logId = savedLog.getId();
        logger.info("Saved initial log for node '{}' with ID {}. State is PENDING.", savedLog.getWorkflowNodeId(), logId);

        // 2. Immediately update the state to RUNNING in the database
        taskExecuteLogDao.updateState(logId, ExecutionState.RUNNING.name());
        logger.info("Updated log state to RUNNING for node '{}' (Log ID: {}).", savedLog.getWorkflowNodeId(), logId);

        // 3. After the current transaction commits, trigger the actual task execution
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                logger.info("Transaction committed for node '{}' (Log ID: {}). Triggering actual execution.", savedLog.getWorkflowNodeId(), logId);
                coreSchedulerService.triggerWorkflowNodeTask(logId);
            }
        });
    }

    /**
     * Handles the completion of a node. This is called by CoreSchedulerService after a node's TaskExecutionJob finishes.
     * It's a wrapper around processNodeCompletion.
     */
    @Transactional
    public void handleNodeCompletion(long completedLogId) {
        logger.info("Handling completion for node log ID: {}", completedLogId);
        processNodeCompletion(completedLogId);
    }

    @Transactional
    public void handleNodeFailure(long failedLogId) {
        TaskExecuteLog failedLog = taskExecuteLogDao.findById(failedLogId)
            .orElseThrow(() -> new IllegalStateException("Failed log with ID " + failedLogId + " not found."));
        
        int instanceId = failedLog.getWorkflowInstanceId();
        String reason = "Node " + failedLog.getWorkflowNodeId() + " failed execution.";
        
        markWorkflowAsFailed(instanceId, reason);
    }

    private boolean isReadyToRun(WorkflowNode node, int instanceId, List<WorkflowEdge> allEdges) {
        List<String> parentNodeIds = allEdges.stream()
                .filter(edge -> edge.getToNodeId().equals(node.getNodeId()))
                .map(WorkflowEdge::getFromNodeId)
                .collect(Collectors.toList());

        if (parentNodeIds.isEmpty()) {
            return true; // It's a start node
        }

        long successfulParents = taskExecuteLogDao.countSuccessfulExecutionsByNodeId(instanceId, parentNodeIds);
        logger.debug("Node '{}' has {} required parents. Found {} successful parent executions for instance {}.", node.getNodeId(), parentNodeIds.size(), successfulParents, instanceId);
        return successfulParents == parentNodeIds.size();
    }

    private boolean isWorkflowComplete(int workflowInstanceId, List<WorkflowNode> allNodes, List<WorkflowEdge> allEdges) {
        // A more robust way to check for completion:
        // The workflow is complete if the number of successfully executed nodes
        // for this instance matches the total number of nodes defined in the workflow.
        
        List<String> allNodeIds = allNodes.stream()
                                          .map(WorkflowNode::getNodeId)
                                          .collect(Collectors.toList());

        if (allNodeIds.isEmpty()) {
            logger.info("Workflow instance {} has no nodes, considering it complete.", workflowInstanceId);
            return true; // A workflow with no nodes is complete by definition.
        }

        long successfulNodesCount = taskExecuteLogDao.countSuccessfulExecutionsByNodeId(workflowInstanceId, allNodeIds);
        
        logger.debug("Checking for workflow completion for instance {}. Total nodes: {}. Successful nodes: {}.", 
                     workflowInstanceId, allNodes.size(), successfulNodesCount);

        return successfulNodesCount >= allNodes.size();
    }

    private List<WorkflowNode> findStartNodes(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return nodes;
        }
        List<String> targetNodeIds = edges.stream().map(WorkflowEdge::getToNodeId).collect(Collectors.toList());
        return nodes.stream()
                .filter(node -> !targetNodeIds.contains(node.getNodeId()))
                .collect(Collectors.toList());
    }
}
