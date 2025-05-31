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
     * Webhook URL(s) for sending notifications to this user.
     * This field can store a single URL string or a JSON array of URL strings
     * (e.g., {@code "http://example.com/webhook"} or {@code ["http://hook1.example.com", "http://hook2.example.com"]}).
     * If configured, task success/failure notifications will be POSTed to these address(es).
     */
    private String webhookAddress;

    /**
     * Stores user-specific notification preferences as a JSON string, allowing configuration for multiple channels.
     * This field is intended for future use with various {@link com.example.taskscheduler.notification.NotificationChannel}s.
     * <p>
     * Example structure:
     * <pre>{@code
     * {
     *   "EMAIL": {"emailAddress": "user@example.com", "enabled": true, "subjectPrefix": "[TaskScheduler]"},
     *   "SLACK": {"channelId": "C123XYZ", "enabled": false}
     * }
     * }</pre>
     * Each key is a channel type (e.g., "EMAIL", "SLACK"), and the value is a map of channel-specific settings.
     * The `enabled` flag within each channel's settings determines if notifications for that channel are active for the user.
     * </p>
     */
    private String notificationPreferencesJson;
}
