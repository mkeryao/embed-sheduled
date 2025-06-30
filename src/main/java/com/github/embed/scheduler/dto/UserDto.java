package com.github.embed.scheduler.dto;

import com.github.embed.scheduler.entity.TaskUser;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for {@link TaskUser}.
 * Used for transferring user data between the client/embed-api layer and the service layer.
 * The password field is used for request input (creating/updating passwords) and should be null in responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    /**
     * The unique identifier for the user.
     */
    private Integer userId;

    /**
     * The username of the user. Must be unique.
     */
    private String username;

    /**
     * The user's password. This field is used when creating a new user or updating an existing user's password.
     * It should be null or omitted in responses to avoid exposing password hashes or raw passwords.
     */
    private String password;

    /**
     * The email address of the user.
     */
    private String email;

    /**
     * Flag indicating if the user has administrative privileges.
     * {@code true} if the user is an admin, {@code false} otherwise.
     */
    private boolean isAdmin;

    /**
     * The webhook address for this user, used for sending notifications.
     * This can be a URL to which task execution status updates are POSTed.
     */
    private String webhookAddress;

    /**
     * Timestamp of when the user account was created. Typically for response only.
     */
    private java.sql.Timestamp createTime;

    /**
     * JSON string for storing user-specific notification preferences.
     * e.g., {"emailOnSuccess": true, "webhookOnFailure": false}
     */
    private String notificationPreferencesJson;
}
