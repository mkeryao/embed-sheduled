package com.example.taskscheduler.dto.workflow;

import com.example.taskscheduler.entity.TaskExecuteLog;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行的数据传输对象，用于在工作流查看器页面展示执行信息
 */
@Data
@NoArgsConstructor
public class WorkflowExecutionDto {
    /**
     * 工作流日志ID
     */
    private Integer logId;

    /**
     * 任务ID
     */
    private Integer taskId;

    /**
     * 工作流名称
     */
    private String workflowName;

    /**
     * 工作流执行状态
     */
    private String state;

    /**
     * 开始时间
     */
    private Timestamp startTime;

    /**
     * 结束时间
     */
    private Timestamp endTime;

    /**
     * 工作流全局参数
     */
    private Map<String, Object> globalParameters;

    /**
     * 工作流节点列表
     */
    private List<WorkflowNode> nodes;

    /**
     * 工作流边列表
     */
    private List<WorkflowEdge> edges;

    /**
     * 工作流步骤执行列表
     */
    private List<TaskExecuteLog> steps;
    
    // Setters and getters
    public Integer getLogId() {
        return logId;
    }

    public void setLogId(Integer logId) {
        this.logId = logId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public void setTaskId(Integer taskId) {
        this.taskId = taskId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getGlobalParameters() {
        return globalParameters;
    }

    public void setGlobalParameters(Map<String, Object> globalParameters) {
        this.globalParameters = globalParameters;
    }

    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes;
    }

    public List<WorkflowEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<WorkflowEdge> edges) {
        this.edges = edges;
    }

    public List<TaskExecuteLog> getSteps() {
        return steps;
    }

    public void setSteps(List<TaskExecuteLog> steps) {
        this.steps = steps;
    }
}
