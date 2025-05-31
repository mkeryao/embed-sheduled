package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskUserDao;
import com.example.taskscheduler.dto.LoginRequest;
import com.example.taskscheduler.dto.AuthResponse;
import com.example.taskscheduler.entity.TaskUser;
import com.example.taskscheduler.util.JwtUtil;
import com.example.taskscheduler.util.PasswordUtil; // Will create this next

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private TaskUserDao taskUserDao;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordUtil passwordUtil;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest.getUsername() == null || loginRequest.getUsername().isEmpty() ||
            loginRequest.getPassword() == null || loginRequest.getPassword().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username and password are required.");
        }

        Optional<TaskUser> userOptional = taskUserDao.findByUsername(loginRequest.getUsername());

        if (!userOptional.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
        }

        TaskUser user = userOptional.get();
        String hashedPassword = passwordUtil.hashPassword(loginRequest.getPassword(), loginRequest.getUsername());

        log.info("hashedPassword {}", hashedPassword);
        if (!hashedPassword.equals(user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password.");
        }

        // If password matches (Note: this is a simplified check as hashing is not fully implemented yet)
        // For now, direct comparison for placeholder, replace with hash check
        // if (!user.getPasswordHash().equals(loginRequest.getPassword())) { // Replace with hashed password check
        //     return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        // }

        final String token = jwtUtil.generateToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
    }
}
