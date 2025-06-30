package com.github.embed.scheduler.controller;

import com.github.embed.scheduler.dao.TaskExecuteLogDao;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus; // Added
import com.github.embed.scheduler.service.TaskStatisticsService;
import com.github.embed.scheduler.dao.TaskConfigDao; // Added
import com.github.embed.scheduler.dto.WorkflowInstanceDetailsDto; // Added
import com.github.embed.scheduler.dto.WorkflowInstanceNodeStatusDto; // Added
import com.github.embed.scheduler.dto.workflow.WorkflowNode; // Added
import com.github.embed.scheduler.dto.workflow.WorkflowEdge; // Added
import com.github.embed.scheduler.entity.TaskConfig; // Added
import com.alibaba.fastjson.JSON; // Added
import org.springframework.util.StringUtils; // Added

import java.util.*;
import java.util.stream.Collectors;
import com.github.embed.scheduler.dto.TaskExecuteLogDto;

import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added

@RestController
@RequestMapping("/embed-api/logs")
public class TaskExecuteLogController {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecuteLogController.class); // Added

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private TaskStatisticsService taskStatisticsService;

    @Autowired // Added
    private TaskConfigDao taskConfigDao; // Added

    @GetMapping
    public ResponseEntity<List<TaskExecuteLogDto>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TaskExecuteLog> allLogs = taskExecuteLogDao.findAll();

        // Create a map of taskId to TaskConfig for efficient lookups
        Set<Integer> taskIds = allLogs.stream()
                .map(TaskExecuteLog::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Integer, TaskConfig> taskConfigMap = taskIds.stream()
                .map(taskConfigDao::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(TaskConfig::getTaskId, tc -> tc));

        // Convert entities to DTOs with taskType
        List<TaskExecuteLogDto> dtoLogs = allLogs.stream()
                .map(log -> {
                    TaskConfig config = taskConfigMap.get(log.getTaskId());
                    return TaskExecuteLogDto.fromEntity(
                            log,
                            config != null ? config.getTaskType() : null,
                            config != null ? config.getTaskName() : null);
                })
                .collect(Collectors.toList());

        int totalLogs = dtoLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<TaskExecuteLogDto> paginatedLogs = dtoLogs.subList(startIndex, endIndex);
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TaskExecuteLogDto>> getLogsByTaskId(
            @PathVariable Integer taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TaskExecuteLog> taskLogs = taskExecuteLogDao.findByTaskId(taskId);

        // Get the TaskConfig for this taskId
        Optional<TaskConfig> taskConfig = taskConfigDao.findById(taskId);
        Integer taskType = taskConfig.map(TaskConfig::getTaskType).orElse(null);
        String taskName = taskConfig.map(TaskConfig::getTaskName).orElse(null);

        // Convert entities to DTOs with taskType
        List<TaskExecuteLogDto> dtoLogs = taskLogs.stream()
                .map(log -> TaskExecuteLogDto.fromEntity(log, taskType, taskName))
                .collect(Collectors.toList());

        int totalLogs = dtoLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<TaskExecuteLogDto> paginatedLogs = dtoLogs.subList(startIndex, endIndex);
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskExecuteLog> getLogById(@PathVariable Long id) {
        return taskExecuteLogDao.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Statistics Endpoints ---

    @GetMapping("/statistics/global-counts")
    public ResponseEntity<Map<String, Long>> getGlobalCounts() { // Changed to specific Map type
        return ResponseEntity.ok(taskStatisticsService.getGlobalExecutionStateCounts());
    }

    @GetMapping("/statistics/task-breakdown")
    public ResponseEntity<List<Map<String, Object>>> getTaskBreakdown() { // Changed to specific List<Map> type
        return ResponseEntity.ok(taskStatisticsService.getTaskBreakdownStatistics());
    }

    @GetMapping("/statistics/top-avg-execution-time")
    public ResponseEntity<List<Map<String, Object>>> getTopAvgExecutionTime(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(taskStatisticsService.getTopNTasksByAverageExecutionTime(limit));
    }

    @GetMapping("/workflow-instance/{workflowLogId}")
    public ResponseEntity<?> getWorkflowInstanceDetails(@PathVariable long workflowLogId) {
        Optional<TaskExecuteLog> mainLogOpt = taskExecuteLogDao.findById(workflowLogId); // Cast to int for current
                                                                                         // findById
        if (!mainLogOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Main workflow log not found with ID: " + workflowLogId);
        }
        TaskExecuteLog mainLog = mainLogOpt.get();
        if (mainLog.getTaskId() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Main workflow log (ID: " + workflowLogId + ") is missing task_id.");
        }

        Optional<TaskConfig> taskConfigOpt = taskConfigDao.findById(mainLog.getTaskId());
        if (!taskConfigOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Workflow task configuration not found for id: " + mainLog.getTaskId());
        }
        TaskConfig workflowConfig = taskConfigOpt.get();
        if (workflowConfig.getTaskType() != 10) { // 10 is Workflow Task type
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Associated task (ID: " + workflowConfig.getTaskId() + ") is not a workflow task.");
        }

        List<WorkflowNode> workflowNodes = new ArrayList<>();
        if (StringUtils.hasText(workflowConfig.getWorkflowNodesJson())) {
            try {
                workflowNodes = JSON.parseArray(workflowConfig.getWorkflowNodesJson(), WorkflowNode.class);
            } catch (Exception e) {
                logger.error("Error parsing workflowNodesJson for workflowLogId {}: {}", workflowLogId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error parsing workflow structure (nodes).");
            }
        }

        List<WorkflowEdge> workflowEdges = new ArrayList<>();
        if (StringUtils.hasText(workflowConfig.getWorkflowEdgesJson())) {
            try {
                workflowEdges = JSON.parseArray(workflowConfig.getWorkflowEdgesJson(), WorkflowEdge.class);
            } catch (Exception e) {
                logger.error("Error parsing workflowEdgesJson for workflowLogId {}: {}", workflowLogId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error parsing workflow structure (edges).");
            }
        }

        List<TaskExecuteLog> nodeAttemptLogs = taskExecuteLogDao.findByParentExecuteNo(workflowLogId);
        Map<String, WorkflowInstanceNodeStatusDto> latestNodeStatuses = new HashMap<>();

        // Initialize all defined nodes with NOT_EXECUTED status
        for (WorkflowNode definedNode : workflowNodes) {
            if (definedNode.getNodeId() == null)
                continue; // Skip invalid nodes
            WorkflowInstanceNodeStatusDto statusDto = new WorkflowInstanceNodeStatusDto();
            statusDto.setNodeId(definedNode.getNodeId());
            statusDto.setStatus("NOT_EXECUTED");
            latestNodeStatuses.put(definedNode.getNodeId(), statusDto);
        }

        // Process logs to find the latest status for each node based on its original
        // taskConfigId
        // Group logs by the task_id of the node log (which corresponds to the original
        // task_config_id of the node's task)
        Map<Integer, List<TaskExecuteLog>> logsByOriginalTaskConfigId = new HashMap<>();
        for (TaskExecuteLog log : nodeAttemptLogs) {
            // Node logs should have task_id set to the original task_config_id it executed
            if (log.getTaskId() == null)
                continue;
            logsByOriginalTaskConfigId.computeIfAbsent(log.getTaskId(), k -> new ArrayList<>()).add(log);
        }

        for (WorkflowNode definedNode : workflowNodes) {
            if (definedNode.getNodeId() == null || definedNode.getTaskConfigId() == null)
                continue;

            List<TaskExecuteLog> attemptsForThisNodeDefinition = logsByOriginalTaskConfigId
                    .get(definedNode.getTaskConfigId());

            if (attemptsForThisNodeDefinition != null && !attemptsForThisNodeDefinition.isEmpty()) {
                // Sort by logId descending to get the latest attempt first
                attemptsForThisNodeDefinition.sort(Comparator.comparing(TaskExecuteLog::getLogId).reversed());
                TaskExecuteLog latestAttempt = attemptsForThisNodeDefinition.get(0);

                WorkflowInstanceNodeStatusDto statusDto = latestNodeStatuses.get(definedNode.getNodeId());
                if (statusDto != null) { // Should always be true due to pre-population
                    statusDto.setStatus(latestAttempt.getState());
                    statusDto.setLastLogId(latestAttempt.getLogId().longValue());
                    statusDto.setLastStartTime(latestAttempt.getStartTime());
                    statusDto.setLastEndTime(latestAttempt.getEndTime());
                    statusDto.setLastMessage(StringUtils.hasText(latestAttempt.getExMsg()) ? latestAttempt.getExMsg()
                            : latestAttempt.getRtnMsg());
                }
            }
        }

        WorkflowInstanceDetailsDto detailsDto = new WorkflowInstanceDetailsDto(
                mainLog.getLogId().longValue(),
                workflowConfig.getTaskId(),
                workflowConfig.getTaskName(),
                workflowNodes,
                workflowEdges,
                new ArrayList<>(latestNodeStatuses.values()));
        return ResponseEntity.ok(detailsDto);
    }
}
