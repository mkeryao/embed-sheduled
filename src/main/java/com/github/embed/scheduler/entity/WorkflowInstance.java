package com.github.embed.scheduler.entity;

import java.sql.Timestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstance {
    private int id;
    private int workflowId;
    private String status;
    private String rtnMsg;
    private Timestamp createTime;
    private Timestamp updateTime;
}
