package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskUser {
    private Integer userId;
    private String username;
    private String passwordHash;
    private String email;
    private boolean isAdmin;
    private Timestamp createTime;
    private String webhookAddress;
}
