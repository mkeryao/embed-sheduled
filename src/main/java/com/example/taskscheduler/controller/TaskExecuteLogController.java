package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowExecutionDto;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/logs")
public class TaskExecuteLogController {

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    
    @Autowired
    private TaskConfigDao taskConfigDao;

    @GetMapping
    public ResponseEntity<List<TaskExecuteLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Basic pagination logic (can be improved with Pageable and proper count for total pages)
        // This is a simplified approach. For robust pagination, use Spring Data JPA's Pageable
        // or manually implement limit/offset queries in DAO.
        // Current DAO does not support pagination directly, so this will fetch all and then sublist.
        // This is INEFFICIENT for large datasets.
        List<TaskExecuteLog> allLogs = taskExecuteLogDao.findAll(); // Assuming this returns all logs for now

        int totalLogs = allLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList()); // Empty list if page is out of bounds
        }

        List<TaskExecuteLog> paginatedLogs = allLogs.subList(startIndex, endIndex);
        // In a real app, also return total pages/elements in a custom response object or headers
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TaskExecuteLog>> getLogsByTaskId(
            @PathVariable Integer taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Similar pagination inefficiency note as above.
        List<TaskExecuteLog> taskLogs = taskExecuteLogDao.findByTaskId(taskId);

        int totalLogs = taskLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList()); // Empty list if page is out of bounds
        }

        List<TaskExecuteLog> paginatedLogs = taskLogs.subList(startIndex, endIndex);
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskExecuteLog> getLogById(@PathVariable Integer id) {
        return taskExecuteLogDao.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/workflow")
    public ResponseEntity<WorkflowExecutionDto> getWorkflowExecution(@PathVariable Integer id) {
        // 获取工作流主日志
        Optional<TaskExecuteLog> workflowLogOpt = taskExecuteLogDao.findById(id);
        if (!workflowLogOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        TaskExecuteLog workflowLog = workflowLogOpt.get();
        Integer taskId = workflowLog.getTaskId();
        
        // 获取工作流任务配置
        Optional<TaskConfig> workflowTaskOpt = taskConfigDao.findById(taskId);
        if (!workflowTaskOpt.isPresent() || workflowTaskOpt.get().getTaskType() != 10) { // 10是工作流类型
            return ResponseEntity.badRequest().build();
        }
        
        TaskConfig workflowTask = workflowTaskOpt.get();
        
        // 构建DTO对象
        WorkflowExecutionDto dto = new WorkflowExecutionDto();
        dto.setLogId(workflowLog.getLogId());
        dto.setTaskId(taskId);
        dto.setWorkflowName(workflowTask.getTaskName());
        dto.setState(workflowLog.getState());
        dto.setStartTime(workflowLog.getStartTime());
        dto.setEndTime(workflowLog.getEndTime());
        
        // 解析全局参数
        if (workflowTask.getGlobalParametersJson() != null && !workflowTask.getGlobalParametersJson().isEmpty()) {
            Map<String, Object> globalParams = JSON.parseObject(workflowTask.getGlobalParametersJson(), 
                    new com.alibaba.fastjson.TypeReference<Map<String, Object>>() {});
            dto.setGlobalParameters(globalParams);
        }
        
        // 解析节点和边
        if (workflowTask.getWorkflowNodesJson() != null && !workflowTask.getWorkflowNodesJson().isEmpty()) {
            List<WorkflowNode> nodes = JSON.parseArray(workflowTask.getWorkflowNodesJson(), WorkflowNode.class);
            dto.setNodes(nodes);
        }
        
        if (workflowTask.getWorkflowEdgesJson() != null && !workflowTask.getWorkflowEdgesJson().isEmpty()) {
            List<WorkflowEdge> edges = JSON.parseArray(workflowTask.getWorkflowEdgesJson(), WorkflowEdge.class);
            dto.setEdges(edges);
        }
        
        // 获取工作流步骤执行日志
        List<TaskExecuteLog> steps = taskExecuteLogDao.findByParentLogId(id);
        dto.setSteps(steps);
        
        return ResponseEntity.ok(dto);
    }
}
