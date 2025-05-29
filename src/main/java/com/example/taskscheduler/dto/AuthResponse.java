package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO representing the response for a successful authentication request.
 * It includes the JWT token and the username of the authenticated user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    /**
     * The JWT (JSON Web Token) generated for the authenticated user.
     * This token should be included in the Authorization header of subsequent API requests.
     */
    private String token;

    /**
     * The username of the authenticated user.
     */
    private String username;
    // Optional: add roles, expiration, etc., if needed in the future.
}
