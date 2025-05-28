package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Integer userId;
    private String username;
    private String password; // Used for request, will be null in response
    private String email;
    private boolean isAdmin;
    private String webhookAddress; // For request and response (response might be masked if sensitive)
    private java.sql.Timestamp createTime; // For response
}
