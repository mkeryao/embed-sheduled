package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dto.TaskConfigDto;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.scheduler.CoreSchedulerService;
import com.alibaba.fastjson.JSON; // Fastjson import
import com.alibaba.fastjson.TypeReference; // Fastjson TypeReference
import com.alibaba.fastjson.JSONException; // Added for beanParameters validation
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added
import org.springframework.util.StringUtils; // Added
import java.util.Map; // Added
import java.util.HashMap; // Added for new endpoint
import java.util.List; // Added
import java.util.ArrayList; // Added for new endpoint
import java.time.LocalDateTime; // Added for new endpoint
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression; // Added for CRON validation
import org.springframework.web.bind.annotation.*;
import com.example.taskscheduler.util.DagCycleDetector; // Added for DAG cycle detection

import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskConfigController {

    private static final Logger logger = LoggerFactory.getLogger(TaskConfigController.class); // Added logger

    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private CoreSchedulerService coreSchedulerService;

    // ObjectMapper is no longer needed here if Fastjson is the primary via HttpMessageConverter
    // @Autowired
    // private ObjectMapper objectMapper;

    // --- DTO Mappers ---
    private TaskConfigDto convertToDto(TaskConfig taskConfig) {
        if (taskConfig == null) return null;
        TaskConfigDto dto = new TaskConfigDto();
        BeanUtils.copyProperties(taskConfig, dto, "workflowNodesJson", "workflowEdgesJson", "globalParametersJson");

        try {
            if (StringUtils.hasText(taskConfig.getWorkflowNodesJson())) {
                dto.setWorkflowNodes(JSON.parseArray(taskConfig.getWorkflowNodesJson(), WorkflowNode.class));
            }
            if (StringUtils.hasText(taskConfig.getWorkflowEdgesJson())) {
                dto.setWorkflowEdges(JSON.parseArray(taskConfig.getWorkflowEdgesJson(), WorkflowEdge.class));
            }
            if (StringUtils.hasText(taskConfig.getGlobalParametersJson())) {
                dto.setGlobalParameters(JSON.parseObject(taskConfig.getGlobalParametersJson(), new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception e) { // Fastjson might throw different exceptions
            logger.error("Error parsing workflow/global params JSON (Fastjson) to DTO for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(), e);
        }
        return dto;
    }

    private TaskConfig convertToEntity(TaskConfigDto dto) {
        if (dto == null) return null;
        TaskConfig entity = new TaskConfig();
        BeanUtils.copyProperties(dto, entity, "workflowNodes", "workflowEdges", "globalParameters");

        try {
            if (dto.getWorkflowNodes() != null && !dto.getWorkflowNodes().isEmpty()) {
                entity.setWorkflowNodesJson(JSON.toJSONString(dto.getWorkflowNodes()));
            } else {
                entity.setWorkflowNodesJson(null);
            }
            if (dto.getWorkflowEdges() != null && !dto.getWorkflowEdges().isEmpty()) {
                entity.setWorkflowEdgesJson(JSON.toJSONString(dto.getWorkflowEdges()));
            } else {
                entity.setWorkflowEdgesJson(null);
            }
            if (dto.getGlobalParameters() != null && !dto.getGlobalParameters().isEmpty()) {
                entity.setGlobalParametersJson(JSON.toJSONString(dto.getGlobalParameters()));
            } else {
                entity.setGlobalParametersJson(null);
            }
        } catch (Exception e) { // Fastjson might throw different exceptions
            logger.error("Error stringifying workflow/global params DTO to JSON (Fastjson) for entity (Task Name {}): {}", dto.getTaskName(), e.getMessage(), e);
        }
        return entity;
    }

    // --- API Endpoints ---

    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody TaskConfigDto taskConfigDto) {
        if (taskConfigDto == null || !StringUtils.hasText(taskConfigDto.getTaskName()) || !StringUtils.hasText(taskConfigDto.getCronExpression())) {
            return ResponseEntity.badRequest().body("Task name and CRON expression must not be empty.");
        }

        // CRON Validation
        if (!CronExpression.isValidExpression(taskConfigDto.getCronExpression())) {
            return ResponseEntity.badRequest().body("Invalid CRON expression format.");
        }

        // beanParameters JSON Validation
        Integer taskType = taskConfigDto.getTaskType();
        String beanParams = taskConfigDto.getBeanParameters();
        if (beanParams != null && !beanParams.trim().isEmpty()) {
            if (taskType != null && (taskType == 2 || taskType == 4)) { // 2 for HTTP, 4 for Shell
                try {
                    JSON.parse(beanParams); // Try to parse to check validity
                } catch (com.alibaba.fastjson.JSONException e) {
                    return ResponseEntity.badRequest().body("beanParameters is not valid JSON for HTTP/Shell task type.");
                }
            }
        }

        // DAG Cycle Validation for Workflow tasks (TaskType 10)
        if (taskConfigDto.getTaskType() != null && taskConfigDto.getTaskType() == 10) {
            List<WorkflowNode> workflowNodes = taskConfigDto.getWorkflowNodes();
            List<WorkflowEdge> workflowEdges = taskConfigDto.getWorkflowEdges();

            if (workflowNodes != null && !workflowNodes.isEmpty() && workflowEdges != null && !workflowEdges.isEmpty()) {
                DagCycleDetector detector = new DagCycleDetector();
                if (detector.hasCycle(workflowNodes, workflowEdges)) {
                    return ResponseEntity.badRequest().body("Workflow configuration contains a cycle. Please correct the workflow definition.");
                }
            }
        }

        TaskConfig taskConfig = convertToEntity(taskConfigDto);
        taskConfig.setTaskId(null); // Ensure it's a new task
        TaskConfig savedTask = taskConfigDao.save(taskConfig);
        if (savedTask.isActive()) {
            coreSchedulerService.scheduleTask(savedTask);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(savedTask));
    }

    @GetMapping
    public ResponseEntity<List<TaskConfigDto>> getAllTasks() {
        List<TaskConfig> tasks = taskConfigDao.findAll();
        List<TaskConfigDto> dtos = tasks.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskConfigDto> getTaskById(@PathVariable Integer id) {
        Optional<TaskConfig> taskOptional = taskConfigDao.findById(id);
        return taskOptional.map(task -> ResponseEntity.ok(convertToDto(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable Integer id, @RequestBody TaskConfigDto taskConfigDto) {
        if (taskConfigDto == null) return ResponseEntity.badRequest().body("Request body cannot be null.");

        // CRON Validation (if cron expression is part of the update)
        if (StringUtils.hasText(taskConfigDto.getCronExpression()) && !CronExpression.isValidExpression(taskConfigDto.getCronExpression())) {
            return ResponseEntity.badRequest().body("Invalid CRON expression format.");
        }

        // beanParameters JSON Validation
        Integer taskType = taskConfigDto.getTaskType();
        String beanParams = taskConfigDto.getBeanParameters();

        // If taskType is not provided in DTO for update, we might need to fetch existing entity to check its type.
        // For simplicity, this validation applies if taskType is explicitly in DTO or if beanParams are being updated.
        // A more robust approach might fetch the existing entity if taskType is null in DTO.
        if (beanParams != null && !beanParams.trim().isEmpty()) {
            Integer effectiveTaskType = taskType;
            if (effectiveTaskType == null) {
                TaskConfig existingTask = taskConfigDao.findById(id).orElse(null);
                if (existingTask != null) {
                    effectiveTaskType = existingTask.getTaskType();
                }
            }

            if (effectiveTaskType != null && (effectiveTaskType == 2 || effectiveTaskType == 4)) { // 2 for HTTP, 4 for Shell
                try {
                    JSON.parse(beanParams); // Try to parse to check validity
                } catch (com.alibaba.fastjson.JSONException e) {
                    return ResponseEntity.badRequest().body("beanParameters is not valid JSON for HTTP/Shell task type.");
                }
            }
        }

        // DAG Cycle Validation for Workflow tasks (TaskType 10)
        // Ensure taskType is determined correctly for updates
        Integer effectiveTaskTypeForCycleCheck = taskConfigDto.getTaskType();
        if (effectiveTaskTypeForCycleCheck == null) {
            TaskConfig existingTaskForType = taskConfigDao.findById(id).orElse(null);
            if (existingTaskForType != null) {
                effectiveTaskTypeForCycleCheck = existingTaskForType.getTaskType();
            }
        }

        if (effectiveTaskTypeForCycleCheck != null && effectiveTaskTypeForCycleCheck == 10) {
            List<WorkflowNode> workflowNodes = taskConfigDto.getWorkflowNodes();
            List<WorkflowEdge> workflowEdges = taskConfigDto.getWorkflowEdges();

            // If nodes/edges are not part of the DTO (e.g. partial update not affecting them),
            // we might need to fetch existing ones to perform a complete cycle check.
            // For now, assume if type is 10, nodes/edges are provided or are being cleared.
            // If workflowNodes or workflowEdges are explicitly null in DTO, it implies clearing them.
            // If they are not present in DTO (partial update), this check might be insufficient
            // without merging with existing entity's nodes/edges first.
            // The current DTO structure and convertToEntity seems to handle full replacements or
            // relies on frontend sending complete node/edge lists if they are part of the update.

            if (workflowNodes != null && !workflowNodes.isEmpty() && workflowEdges != null && !workflowEdges.isEmpty()) {
                DagCycleDetector detector = new DagCycleDetector();
                if (detector.hasCycle(workflowNodes, workflowEdges)) {
                    return ResponseEntity.badRequest().body("Workflow configuration contains a cycle. Please correct the workflow definition.");
                }
            }
            // If workflowNodes or workflowEdges are cleared (e.g. to empty lists or null),
            // that's a valid state (no cycle).
        }

        Optional<TaskConfig> existingTaskOptional = taskConfigDao.findById(id);
        if (!existingTaskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        TaskConfig taskConfigToUpdate = convertToEntity(taskConfigDto);
        taskConfigToUpdate.setTaskId(id); // Ensure ID is set for update

        // Preserve fields not typically updated or if they are null in DTO but set in DB
        TaskConfig existingEntity = existingTaskOptional.get();
        if (taskConfigToUpdate.getTaskName() == null) taskConfigToUpdate.setTaskName(existingEntity.getTaskName());
        if (taskConfigToUpdate.getCronExpression() == null) taskConfigToUpdate.setCronExpression(existingEntity.getCronExpression());
        // Add other fields as necessary to preserve from existingEntity if not provided in DTO

        int updatedRows = taskConfigDao.update(taskConfigToUpdate);
        if (updatedRows == 0) {
            // Should not happen if findById was successful, but as a safeguard
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        // Reschedule or cancel based on new state
        TaskConfig updatedTask = taskConfigDao.findById(id).orElse(taskConfigToUpdate); // Fetch the fully updated task
        coreSchedulerService.rescheduleTask(updatedTask); // rescheduleTask handles active/inactive logic

        return ResponseEntity.ok(convertToDto(updatedTask));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Integer id) {
        Optional<TaskConfig> taskOptional = taskConfigDao.findById(id);
        if (!taskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        coreSchedulerService.cancelTask(id);
        taskConfigDao.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<String> triggerTask(@PathVariable Integer id) {
        Optional<TaskConfig> taskOptional = taskConfigDao.findById(id);
        if (!taskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        try {
            coreSchedulerService.triggerTaskManually(id);
            return ResponseEntity.ok("Task " + id + " triggered successfully.");
        } catch (IllegalArgumentException e) {
             return ResponseEntity.notFound().build(); // If task not found by triggerTaskManually
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error triggering task " + id + ": " + e.getMessage());
        }
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<String> enableTask(@PathVariable Integer id) {
        Optional<TaskConfig> taskOptional = taskConfigDao.findById(id);
        if (!taskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        TaskConfig task = taskOptional.get();
        if (task.isActive()) {
            return ResponseEntity.ok("Task " + id + " is already enabled.");
        }
        task.setActive(true);
        taskConfigDao.updateTaskStatus(id, true);
        coreSchedulerService.scheduleTask(task); // Schedule it
        return ResponseEntity.ok("Task " + id + " enabled successfully.");
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<String> disableTask(@PathVariable Integer id) {
        Optional<TaskConfig> taskOptional = taskConfigDao.findById(id);
        if (!taskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        TaskConfig task = taskOptional.get();
        if (!task.isActive()) {
            return ResponseEntity.ok("Task " + id + " is already disabled.");
        }
        task.setActive(false);
        coreSchedulerService.cancelTask(id); // Cancel it from scheduler
        taskConfigDao.updateTaskStatus(id, false); // Then update DB
        return ResponseEntity.ok("Task " + id + " disabled successfully.");
    }

    @GetMapping("/validate-cron")
    public ResponseEntity<Map<String, Object>> validateCronAndGetNextTimes(
            @RequestParam String cronExpression,
            @RequestParam(defaultValue = "5") int count) {

        Map<String, Object> response = new HashMap<>();
        if (!StringUtils.hasText(cronExpression)) {
            response.put("isValid", false);
            response.put("error", "CRON expression cannot be empty.");
            return ResponseEntity.badRequest().body(response);
        }

        boolean isValid = CronExpression.isValidExpression(cronExpression);
        response.put("isValid", isValid);

        if (isValid) {
            try {
                CronExpression cron = CronExpression.parse(cronExpression);
                List<String> nextTimes = new ArrayList<>();
                LocalDateTime next = LocalDateTime.now();
                for (int i = 0; i < count; i++) {
                    next = cron.next(next);
                    if (next == null) { // Should ideally not happen with a valid expression that has future dates
                        break;
                    }
                    nextTimes.add(next.toString());
                }
                response.put("nextExecutionTimes", nextTimes);
                return ResponseEntity.ok(response);
            } catch (IllegalArgumentException e) {
                // This catch might be redundant if isValidExpression is comprehensive
                // but good as a safeguard if parse has stricter checks or for unforeseen issues.
                response.put("isValid", false); // Correct the status if parse fails
                response.put("error", "Failed to parse CRON expression or determine next execution time: " + e.getMessage());
                // Still return 200 OK as it's a validation endpoint, but indicate failure in body
                return ResponseEntity.ok(response);
            }
        } else {
            response.put("error", "Invalid CRON expression format.");
            // Return 200 OK with isValid:false, as per common validation endpoint patterns
            return ResponseEntity.ok(response);
        }
    }
}
