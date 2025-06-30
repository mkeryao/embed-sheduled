package com.github.embed.scheduler.dto;

import com.github.embed.scheduler.dto.workflow.WorkflowEdge;
import com.github.embed.scheduler.dto.workflow.WorkflowNode;
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
