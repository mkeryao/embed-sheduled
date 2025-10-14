package com.github.embed.scheduler.service;

import com.alibaba.fastjson.JSON;
import com.github.embed.scheduler.dto.workflow.WorkflowNode;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.enums.ExecutionPattern;
import com.github.embed.scheduler.enums.ExecutionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WorkflowAsyncExecutor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAsyncExecutor.class);

    private final WorkflowExecutionService workflowExecutionService;

    @Autowired
    public WorkflowAsyncExecutor(@Lazy WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    /**
     * Asynchronously attempts to acquire a database lock (by inserting a PENDING record)
     * and then executes the node. This method does NOT manage the transaction itself,
     * allowing it to catch exceptions from the transactional `executeNode` method
     * without causing a rollback on a caught DuplicateKeyException.
     */
    @Async
    public void tryAcquireLockAndExecuteNode(WorkflowNode node, int workflowId, Long instanceId, Long parentWorkflowLogId) {
        logger.debug("Attempting to acquire lock for node '{}' in instance {}", node.getNodeId(), instanceId);
        try {
            // This call is to a @Transactional method. It will attempt to insert the record and run the node.
            // If it throws DuplicateKeyException, we catch it below.
            workflowExecutionService.executeNode(node, workflowId, instanceId, parentWorkflowLogId);
            logger.info("Successfully acquired lock and initiated execution for node '{}'.", node.getNodeId());

        } catch (DuplicateKeyException e) {
            // This is the expected exception when another thread has already inserted the record.
            logger.warn("Failed to acquire lock for node '{}' in instance {}. Another thread got it first. This is normal in concurrent scenarios.", node.getNodeId(), instanceId);
            // We can safely ignore this exception, as it means another thread is already processing this node.
        } catch (Exception e) {
            // Catch any other unexpected exceptions during the lock acquisition and execution phase.
            logger.error("An unexpected error occurred while trying to execute node '{}' in instance {}.", node.getNodeId(), instanceId, e);
            // Mark the workflow as failed if a node execution fails unexpectedly.
            workflowExecutionService.markWorkflowAsFailed(instanceId, "Node " + node.getNodeId() + " failed with exception: " + e.getMessage());
        }
    }

    @Async
    public void processNodeCompletion(long completedLogId) {
        // This method now simply delegates to the main service.
        // The actual logic is within a transaction in the main service.
        workflowExecutionService.processNodeCompletion(completedLogId);
    }
}
