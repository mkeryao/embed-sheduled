package com.example.taskscheduler.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger; // Fastjson import
import org.slf4j.LoggerFactory; // Fastjson TypeReference
import org.springframework.beans.BeanUtils; // Added
import org.springframework.beans.factory.annotation.Autowired; // Added
import org.springframework.http.HttpStatus; // Added
import org.springframework.http.ResponseEntity; // Added
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping; // 添加CronExpression导入
import org.springframework.web.bind.annotation.PutMapping; // 添加LocalDateTime导入
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dto.TaskConfigDto;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.scheduler.CoreSchedulerService;

@RestController
@RequestMapping("/api/tasks")
public class TaskConfigController {

    private static final Logger logger = LoggerFactory.getLogger(TaskConfigController.class); // Added logger    @Autowired
    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private CoreSchedulerService coreSchedulerService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        if (taskConfigDto == null) return ResponseEntity.badRequest().build();
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

    /**
     * 获取任务的未来5次执行时间
     * @param cronExpression Cron表达式
     * @return 未来5次执行时间列表
     */    @GetMapping("/next-execution-times")
    public ResponseEntity<?> getNextExecutionTimes(@RequestParam String cronExpression) {
        try {
            if (!CronExpression.isValidExpression(cronExpression)) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "无效的Cron表达式: " + cronExpression);
                return ResponseEntity.badRequest().body(response);
            }
            
            CronExpression cron = CronExpression.parse(cronExpression);
            List<String> executionTimes = new ArrayList<>();
            
            LocalDateTime nextTime = LocalDateTime.now();
            for (int i = 0; i < 5; i++) {
                nextTime = cron.next(nextTime);
                if (nextTime != null) {
                    executionTimes.add(nextTime.toString());
                } else {
                    break;
                }
            }
            
            return ResponseEntity.ok(executionTimes);
        } catch (Exception e) {
            logger.error("计算执行时间出错", e);
            Map<String, String> response = new HashMap<>();
            response.put("message", "计算执行时间出错: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * 验证Cron表达式是否有效
     * @param cronExpression Cron表达式
     * @return 验证结果
     */
    @GetMapping("/validate-cron")
    public ResponseEntity<?> validateCron(@RequestParam String cronExpression) {
        boolean isValid = CronExpression.isValidExpression(cronExpression);
        Map<String, Boolean> response = new HashMap<>();
        response.put("valid", isValid);
        return ResponseEntity.ok(response);
    }    /**
     * 获取任务相关信息用于UI下拉框选择
     * @return 包含用户列表、日历列表等信息
     */
    @GetMapping("/form-data")
    public ResponseEntity<?> getFormData() {
        try {
            Map<String, Object> formData = new HashMap<>();
            
            // 使用JdbcTemplate直接查询数据库
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT user_id, username FROM task_user");
                
            List<Map<String, Object>> calendars = jdbcTemplate.queryForList(
                "SELECT calendar_id, calendar_name FROM task_calendar");
                
            List<Map<String, Object>> beanTasks = jdbcTemplate.queryForList(
                "SELECT task_id, task_name, bean_name FROM task_config WHERE task_type=0");
            
            formData.put("users", users);
            formData.put("calendars", calendars);
            formData.put("beanTasks", beanTasks);
            
            return ResponseEntity.ok(formData);
        } catch (Exception e) {
            logger.error("获取表单数据出错", e);
            Map<String, String> response = new HashMap<>();
            response.put("message", "获取表单数据出错: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
