package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Timestamp;

/**
 * Entity representing a user of the task scheduler system.
 * Users can be administrators or regular users, and can be configured
 * for notifications via webhooks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskUser {
    /** Unique identifier for the user. */
    private Integer userId;

    /** Unique username for login. */
    private String username;

    /** Hashed password for the user. Raw passwords are not stored. */
    private String passwordHash;

    /** Email address of the user. */
    private String email;

    /** Flag indicating if the user has administrative privileges. */
    private boolean isAdmin;

    /** Timestamp of when this user account was created. */
    private Timestamp createTime;

    /** 
     * Webhook URL for sending notifications to this user. 
     * If configured, task success/failure notifications will be POSTed to this address.
     */
    private String webhookAddress;
}
