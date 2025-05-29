package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO representing the request body for user login.
 * It contains the username and password provided by the user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    /**
     * The username of the user attempting to log in.
     */
    private String username;

    /**
     * The raw password provided by the user.
     */
    private String password;
}
