package com.example.taskscheduler.controller;

import com.alibaba.fastjson.JSONObject;
import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流可视化控制器
 * 提供工作流图表所需的数据接口
 */
@RestController
@RequestMapping("/tasks")
public class WorkflowVisualizationController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowVisualizationController.class);

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private TaskConfigDao taskConfigDao;

    /**
     * 获取工作流执行的详细信息，包括节点、边和执行步骤
     * @param logId 工作流执行日志ID
     * @return 工作流可视化所需的完整数据
     */
    @GetMapping("/logs/{logId}/workflow")
    public ResponseEntity<?> getWorkflowVisualization(@PathVariable Integer logId) {
        // 1. 获取主工作流日志
        Optional<TaskExecuteLog> workflowLogOpt = taskExecuteLogDao.findById(logId);
        if (!workflowLogOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        TaskExecuteLog workflowLog = workflowLogOpt.get();
        
        // 2. 检查是否为工作流任务
        Optional<TaskConfig> taskConfigOpt = taskConfigDao.findById(workflowLog.getTaskId());
        if (!taskConfigOpt.isPresent() || taskConfigOpt.get().getTaskType() != 10) { // 10 = WORKFLOW
            return ResponseEntity.badRequest()
                    .body(new JSONObject().fluentPut("error", "指定的日志不是工作流任务"));
        }

        TaskConfig workflowConfig = taskConfigOpt.get();

        // 3. 解析工作流定义
        List<WorkflowNode> nodes = parseNodesFromJson(workflowConfig.getWorkflowNodesJson());
        List<WorkflowEdge> edges = parseEdgesFromJson(workflowConfig.getWorkflowEdgesJson());
        Map<String, Object> globalParameters = parseGlobalParamsFromJson(workflowConfig.getGlobalParametersJson());

        // 4. 获取工作流的所有步骤执行日志
        List<TaskExecuteLog> stepLogs = taskExecuteLogDao.findByParentLogId(logId);

        // 5. 增强步骤日志信息
        List<Map<String, Object>> enhancedSteps = enhanceStepLogs(stepLogs, nodes);

        // 6. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("logId", workflowLog.getLogId());
        result.put("taskId", workflowLog.getTaskId());
        result.put("workflowName", workflowConfig.getTaskName());
        result.put("state", workflowLog.getState());
        result.put("startTime", workflowLog.getStartTime());
        result.put("endTime", workflowLog.getEndTime());
        result.put("message", workflowLog.getExMsg());
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("globalParameters", globalParameters);
        result.put("steps", enhancedSteps);

        return ResponseEntity.ok(result);
    }

    /**
     * 解析工作流节点定义
     */
    private List<WorkflowNode> parseNodesFromJson(String nodesJson) {
        if (!StringUtils.hasText(nodesJson)) {
            return Collections.emptyList();
        }

        try {
            return JSON.parseArray(nodesJson, WorkflowNode.class);
        } catch (Exception e) {
            logger.error("解析工作流节点JSON失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析工作流边定义
     */
    private List<WorkflowEdge> parseEdgesFromJson(String edgesJson) {
        if (!StringUtils.hasText(edgesJson)) {
            return Collections.emptyList();
        }

        try {
            return JSON.parseArray(edgesJson, WorkflowEdge.class);
        } catch (Exception e) {
            logger.error("解析工作流边JSON失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析工作流全局参数
     */
    private Map<String, Object> parseGlobalParamsFromJson(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return Collections.emptyMap();
        }

        try {
            return JSON.parseObject(paramsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.error("解析工作流全局参数JSON失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 增强步骤日志信息，添加节点名称等
     */
    private List<Map<String, Object>> enhanceStepLogs(List<TaskExecuteLog> stepLogs, List<WorkflowNode> nodes) {
        // 创建节点ID到节点名称的映射
        Map<String, String> nodeNameMap = new HashMap<>();
        Map<Integer, String> taskIdToNodeIdMap = new HashMap<>();
        
        for (WorkflowNode node : nodes) {
            nodeNameMap.put(node.getNodeId(), node.getNodeName());
            taskIdToNodeIdMap.put(node.getTaskConfigId(), node.getNodeId());
        }

        // 转换并增强日志
        return stepLogs.stream().map(log -> {
            Map<String, Object> enhancedLog = new HashMap<>();
            enhancedLog.put("logId", log.getLogId());
            enhancedLog.put("taskId", log.getTaskId());
            enhancedLog.put("state", log.getState());
            enhancedLog.put("startTime", log.getStartTime());
            enhancedLog.put("endTime", log.getEndTime());
            enhancedLog.put("message", log.getExMsg());
            enhancedLog.put("instanceId", log.getInstanceId());

            // 尝试从日志记录中的附加信息中找到节点ID
            String nodeId = null;
            if (StringUtils.hasText(log.getParams())) {
                try {
                    Map<String, Object> params = JSON.parseObject(log.getParams(), new TypeReference<Map<String, Object>>() {});
                    if (params.containsKey("nodeId")) {
                        nodeId = String.valueOf(params.get("nodeId"));
                    }
                } catch (Exception e) {
                    logger.warn("解析步骤参数失败: {}", e.getMessage());
                }
            }

            // 如果没有在参数中找到，则尝试通过任务ID查找
            if (nodeId == null) {
                nodeId = taskIdToNodeIdMap.get(log.getTaskId());
            }

            enhancedLog.put("nodeId", nodeId);
            enhancedLog.put("nodeName", nodeId != null ? nodeNameMap.get(nodeId) : null);

            return enhancedLog;
        }).collect(Collectors.toList());
    }
}
