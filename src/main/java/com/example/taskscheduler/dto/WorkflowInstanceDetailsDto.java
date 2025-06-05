package com.example.taskscheduler.dto;

import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceDetailsDto {
    private Long mainWorkflowLogId;
    private Integer workflowTaskConfigId;
    private String workflowTaskName;
    private List<WorkflowNode> workflowNodes;
    private List<WorkflowEdge> workflowEdges;
    private List<WorkflowInstanceNodeStatusDto> nodeStatuses;
}
