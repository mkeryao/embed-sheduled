package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dto.TaskConfigDto;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.scheduler.CoreSchedulerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.* ;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/tasks")
public class TaskConfigController {
    private static final Logger logger =  LoggerFactory.getLogger(TaskConfigController.class);

    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private CoreSchedulerService coreSchedulerService;

    @Autowired
    private ObjectMapper objectMapper; // For JSON conversion

    // --- DTO Mappers ---
    private TaskConfigDto convertToDto(TaskConfig taskConfig) {
        if (taskConfig == null){
            return null;
        }
        TaskConfigDto dto = new TaskConfigDto();
        BeanUtils.copyProperties(taskConfig, dto, "workflowNodesJson", "workflowEdgesJson", "globalParametersJson");

        try {
            if (StringUtils.hasText(taskConfig.getWorkflowNodesJson())) {
                dto.setWorkflowNodes(objectMapper.readValue(taskConfig.getWorkflowNodesJson(), new TypeReference<List<WorkflowNode>>() {}));
            }
            if (StringUtils.hasText(taskConfig.getWorkflowEdgesJson())) {
                dto.setWorkflowEdges(objectMapper.readValue(taskConfig.getWorkflowEdgesJson(), new TypeReference<List<WorkflowEdge>>() {}));
            }
            if (StringUtils.hasText(taskConfig.getGlobalParametersJson())) {
                dto.setGlobalParameters(objectMapper.readValue(taskConfig.getGlobalParametersJson(), new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception e) {
            logger.error("Error parsing workflow/global params JSON to DTO for task ID {}: {}", taskConfig.getTaskId(), e.getMessage(), e);
        }
        return dto;
    }

    private TaskConfig convertToEntity(TaskConfigDto dto) {
        if (dto == null) {
            return null;
        }
        TaskConfig entity = new TaskConfig();
        BeanUtils.copyProperties(dto, entity, "workflowNodes", "workflowEdges", "globalParameters");

        try {
            if (dto.getWorkflowNodes() != null && !dto.getWorkflowNodes().isEmpty()) {
                entity.setWorkflowNodesJson(objectMapper.writeValueAsString(dto.getWorkflowNodes()));
            } else {
                entity.setWorkflowNodesJson(null); // Ensure empty or null list results in null JSON
            }
            if (dto.getWorkflowEdges() != null && !dto.getWorkflowEdges().isEmpty()) {
                entity.setWorkflowEdgesJson(objectMapper.writeValueAsString(dto.getWorkflowEdges()));
            } else {
                entity.setWorkflowEdgesJson(null);
            }
            if (dto.getGlobalParameters() != null && !dto.getGlobalParameters().isEmpty()) {
                entity.setGlobalParametersJson(objectMapper.writeValueAsString(dto.getGlobalParameters()));
            } else {
                entity.setGlobalParametersJson(null);
            }
        } catch (Exception e) {
            logger.error("Error stringifying workflow/global params DTO to JSON for entity (Task Name {}): {}", dto.getTaskName(), e.getMessage(), e);
        }
        return entity;
    }

    // --- API Endpoints ---

    @PostMapping
    public ResponseEntity<TaskConfigDto> createTask(@RequestBody TaskConfigDto taskConfigDto) {
        if (taskConfigDto == null || taskConfigDto.getTaskName() == null || taskConfigDto.getCronExpression() == null) {
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<TaskConfigDto> updateTask(@PathVariable Integer id, @RequestBody TaskConfigDto taskConfigDto) {
        if (taskConfigDto == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<TaskConfig> existingTaskOptional = taskConfigDao.findById(id);
        if (!existingTaskOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        TaskConfig taskConfigToUpdate = convertToEntity(taskConfigDto);
        taskConfigToUpdate.setTaskId(id); // Ensure ID is set for update

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
}
