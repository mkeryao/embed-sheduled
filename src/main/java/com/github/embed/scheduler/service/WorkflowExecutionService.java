package com.github.embed.scheduler.service;

import com.alibaba.fastjson.JSON;
import com.github.embed.scheduler.dao.TaskConfigDao;
import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.dao.TaskWorkflowNodeStateDao;
import com.github.embed.scheduler.dto.workflow.WorkflowEdge;
import com.github.embed.scheduler.dto.workflow.WorkflowNode;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
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
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private TaskWorkflowNodeStateDao taskWorkflowNodeStateDao;

    @Autowired
    private WorkflowAsyncExecutor asyncExecutor;

    @Autowired
    private CoreSchedulerService coreSchedulerService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DistributedLockService distributedLockService;

    @Transactional(transactionManager = "schedulerTransactionManager")
    public void startWorkflow(int workflowId, Long parentLogId) {
        logger.info("Attempting to start workflow with ID: {} for parent log ID: {}", workflowId, parentLogId);

        // 1. Use the provided parent log ID as the workflow instance ID.
        final Long instanceId = parentLogId;
        TaskExecuteLog parentWorkflowLog = taskExecuteLogDao.findById(parentLogId)
                .orElseThrow(() -> new IllegalArgumentException("Parent log with ID " + parentLogId + " not found."));

        // Ensure the parent log is marked as a workflow parent and is running
        parentWorkflowLog.setTaskPattern(ExecutionPattern.WORKFLOW_PARENT);
        parentWorkflowLog.setState(ExecutionState.RUNNING);
        parentWorkflowLog.setWorkflowInstanceId(instanceId); // Self-reference for consistency
        parentWorkflowLog.setInstanceId(distributedLockService.getSchedulerInstanceId());
        taskExecuteLogDao.update(parentWorkflowLog);
        logger.info("Using parent workflow log ID {} as instance ID.", instanceId);

        TaskConfig workflowConfig = taskConfigDao.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow with ID " + workflowId + " not found."));

        if (!StringUtils.hasText(workflowConfig.getWorkflowNodesJson())) {
            logger.warn("Workflow {} has no nodes defined. Completing immediately.", workflowId);
            parentWorkflowLog.setState(ExecutionState.SUCCESS);
            parentWorkflowLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
            taskExecuteLogDao.update(parentWorkflowLog);
            return;
        }

        List<WorkflowNode> nodes = JSON.parseArray(workflowConfig.getWorkflowNodesJson(), WorkflowNode.class);
        List<WorkflowEdge> edges = StringUtils.hasText(workflowConfig.getWorkflowEdgesJson())
                ? JSON.parseArray(workflowConfig.getWorkflowEdgesJson(), WorkflowEdge.class)
                : java.util.Collections.emptyList();

        initializeNodeStates(instanceId, nodes, edges);

        List<WorkflowNode> startNodes = findStartNodes(nodes, edges);
        logger.info("Found {} start nodes for workflow ID: {}", startNodes.size(), workflowId);

        if (startNodes.isEmpty() && !nodes.isEmpty()) {
            logger.warn("Workflow {} has nodes but no start nodes (potential cycle). Cannot start.", workflowId);
            parentWorkflowLog.setState(ExecutionState.FAILED);
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

    private void initializeNodeStates(Long instanceId, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        Map<String, Long> inDegrees = nodes.stream()
                .collect(Collectors.toMap(WorkflowNode::getNodeId, node -> 0L));

        for (WorkflowEdge edge : edges) {
            inDegrees.computeIfPresent(edge.getToNodeId(), (k, v) -> v + 1);
        }

        List<com.github.embed.scheduler.entity.TaskWorkflowNodeState> nodeStates = nodes.stream().map(node -> {
            com.github.embed.scheduler.entity.TaskWorkflowNodeState state = new com.github.embed.scheduler.entity.TaskWorkflowNodeState();
            state.setWorkflowInstanceId(instanceId);
            state.setNodeId(node.getNodeId());
            long pending = inDegrees.get(node.getNodeId());
            state.setPendingParents((int) pending);
            state.setStatus(pending == 0 ? ExecutionState.READY.name() : ExecutionState.PENDING.name());
            return state;
        }).collect(Collectors.toList());

        taskWorkflowNodeStateDao.batchCreate(nodeStates);
        logger.info("Initialized {} node states for workflow instance {}", nodeStates.size(), instanceId);
    }

    /**
     * Creates the initial log for a start node and then calls the main execution logic.
     * This is the entry point for nodes that don't have dependencies.
     */
    @Transactional(transactionManager = "schedulerTransactionManager")
    public void executeStartNode(WorkflowNode node, int workflowId, Long workflowInstanceId, Long parentWorkflowLogId) {
        // The save is now part of executeNode's transaction
        executeNode(node, workflowId, workflowInstanceId, parentWorkflowLogId);
    }

    @Transactional(transactionManager = "schedulerTransactionManager")
    public void processNodeCompletion(long completedLogId) {
        TaskExecuteLog completedLog = taskExecuteLogDao.findById(completedLogId)
                .orElseThrow(() -> new IllegalStateException("Completed log with ID " + completedLogId + " not found."));

        Long instanceId = completedLog.getWorkflowInstanceId();
        String completedNodeId = completedLog.getWorkflowNodeId();

        if (instanceId == null) {
            logger.warn("Completed log {} is not part of a workflow (workflow_instance_id is null). Aborting completion processing.", completedLogId);
            return;
        }

        if (completedLog.getState() != ExecutionState.SUCCESS) {
            logger.warn("Node with LogId {} did not complete successfully (state: {}). Halting this path.", completedLogId, completedLog.getState());
            taskWorkflowNodeStateDao.updateStatus(instanceId, completedNodeId, ExecutionState.FAILED.name());
            // Mark workflow as failed
            markWorkflowAsFailed(instanceId, "Node " + completedNodeId + " failed. [" +  completedLog.getExMsg() + "]");
            return;
        }

        // Mark the node as successfully completed in the state table
        taskWorkflowNodeStateDao.updateStatus(instanceId, completedNodeId, ExecutionState.SUCCESS.name());
        logger.info("Marked node '{}' as SUCCESS in state tracking for instance {}.", completedNodeId, instanceId);

        int workflowId = completedLog.getWorkflowId();
        Long parentWorkflowLogId = completedLog.getParentLogId() != null ? completedLog.getParentLogId().longValue() : null;

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
            if (downstreamNode != null) {
                int newPendingCount = taskWorkflowNodeStateDao.decrementAndGetPendingParents(instanceId, downstreamNode.getNodeId());
                if (newPendingCount == 0) {
                    logger.info("Downstream node '{}' is ready to run. Triggering async lock acquisition.", downstreamNode.getNodeId());
                    taskWorkflowNodeStateDao.updateStatus(instanceId, downstreamNode.getNodeId(), ExecutionState.READY.name());
                    asyncExecutor.tryAcquireLockAndExecuteNode(downstreamNode, workflowId, instanceId, parentWorkflowLogId);
                } else {
                    logger.info("Downstream node '{}' is not yet ready to run. Waiting for {} more dependencies.", downstreamNode.getNodeId(), newPendingCount);
                }
            }
        }

        if (isWorkflowComplete(instanceId)) {
            logger.info("Workflow instance {} is complete. Updating status.", instanceId);
            markWorkflowAsComplete(instanceId);
        }
    }

    /**
     * Marks a workflow instance and its parent log as failed.
     */
    @Transactional(transactionManager = "schedulerTransactionManager")
    public void markWorkflowAsFailed(Long instanceId, String reason) {
        TaskExecuteLog parentLog = taskExecuteLogDao.findById(instanceId).orElse(null);
        if (parentLog != null && parentLog.getState() != ExecutionState.FAILED) {
            parentLog.setState(ExecutionState.FAILED);
            parentLog.setRtnMsg(reason);
            parentLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
            taskExecuteLogDao.update(parentLog);
            logger.warn("Marked workflow instance {} (Parent Log ID) as FAILED. Reason: {}", instanceId, reason);

            // Also send a notification for the parent workflow failure
            TaskConfig parentTaskConfig = taskConfigDao.findById(parentLog.getTaskId())
                    .orElse(null);
            if (parentTaskConfig != null && StringUtils.hasText( parentTaskConfig.getNotifyFailedUserIds())) {
                notificationService.sendFailureNotification(parentTaskConfig, parentLog);
            }
        }
    }

    /**
     * Marks a workflow instance and its parent log as complete.
     */
    @Transactional(transactionManager = "schedulerTransactionManager")
    public void markWorkflowAsComplete(Long instanceId) {
        TaskExecuteLog parentLog = taskExecuteLogDao.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Parent workflow log " + instanceId + " not found."));

        if (parentLog.getState() != ExecutionState.SUCCESS) {
            parentLog.setState(ExecutionState.SUCCESS);
            parentLog.setRtnMsg("Workflow completed successfully.");
            parentLog.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
            taskExecuteLogDao.update(parentLog);
            logger.info("Marked workflow instance {} (Parent Log ID) as COMPLETED.", instanceId);

            // Send success notification for the parent workflow
            TaskConfig parentTaskConfig = taskConfigDao.findById(parentLog.getTaskId())
                    .orElse(null);
            if (parentTaskConfig != null && StringUtils.hasText(parentTaskConfig.getNotifySuccessUserIds())) {
                notificationService.sendSuccessNotification(parentTaskConfig, parentLog);
            }
        }
    }

    /**
     * Executes the logic for a given node, identified by its log entry.
     * This method is transactional and will attempt to save the PENDING log first.
     * If the save fails due to a duplicate key, it will throw a DuplicateKeyException,
     * which is caught by the async caller.
     * @param node The node to execute.
     * @param workflowId The ID of the workflow definition.
     * @param instanceId The unique ID for this workflow run.
     * @param parentWorkflowLogId The log ID of the parent workflow trigger.
     */
    @Transactional(transactionManager = "schedulerTransactionManager")
    public void executeNode(WorkflowNode node, int workflowId, Long instanceId, Long parentWorkflowLogId) {
        // 1. Update state tracking table to RUNNING
        taskWorkflowNodeStateDao.updateStatus(instanceId, node.getNodeId(), ExecutionState.RUNNING.name());
        logger.info("Updated node state to RUNNING for node '{}' in state tracking (Instance ID: {}).", node.getNodeId(), instanceId);

        // 2. Create and save the log entry with RUNNING state directly.
        // The unique constraint on (workflow_instance_id, workflow_node_id) will prevent duplicates.
        TaskExecuteLog nodeLog = new TaskExecuteLog();
        nodeLog.setTaskId(node.getTaskConfigId());
        nodeLog.setWorkflowId(workflowId);
        nodeLog.setWorkflowInstanceId(instanceId);
        nodeLog.setWorkflowNodeId(node.getNodeId());
        nodeLog.setStartTime(new java.sql.Timestamp(System.currentTimeMillis())); // Fix: Set start time
        nodeLog.setState(ExecutionState.RUNNING); // Set to RUNNING directly
        nodeLog.setTaskPattern(ExecutionPattern.WORKFLOW_STEP);
        nodeLog.setParentLogId(parentWorkflowLogId != null ? parentWorkflowLogId.intValue() : null);
        nodeLog.setInstanceId(distributedLockService.getSchedulerInstanceId());
        if (node.getParameters() != null && !node.getParameters().isEmpty()) {
            nodeLog.setParameters(JSON.toJSONString(node.getParameters()));
        }

        TaskExecuteLog savedLog = taskExecuteLogDao.save(nodeLog);
        long logId = savedLog.getId();
        logger.info("Saved RUNNING log for node '{}' with ID {}.", savedLog.getWorkflowNodeId(), logId);

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
    @Transactional(transactionManager = "schedulerTransactionManager")
    public void handleNodeCompletion(long completedLogId) {
        logger.info("Handling completion for node log ID: {}", completedLogId);
        processNodeCompletion(completedLogId);
    }

    @Transactional(transactionManager = "schedulerTransactionManager")
    public void handleNodeFailure(long failedLogId) {
        TaskExecuteLog failedLog = taskExecuteLogDao.findById(failedLogId)
                .orElseThrow(() -> new IllegalStateException("Failed log with ID " + failedLogId + " not found."));

        Long instanceId = failedLog.getWorkflowInstanceId();
        if (instanceId == null) {
            logger.warn("Failed log {} is not part of a workflow. Cannot process failure.", failedLogId);
            return;
        }
        String nodeId = failedLog.getWorkflowNodeId();
        String reason = "Node " + nodeId + " failed execution.";

        taskWorkflowNodeStateDao.updateStatus(instanceId, nodeId, ExecutionState.FAILED.name());
        markWorkflowAsFailed(instanceId, reason);
    }

    private boolean isWorkflowComplete(Long workflowInstanceId) {
        // The workflow is complete if there are no nodes in PENDING, READY, or RUNNING state.
        return taskWorkflowNodeStateDao.isWorkflowComplete(workflowInstanceId);
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
