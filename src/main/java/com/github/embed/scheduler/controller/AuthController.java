package com.github.embed.scheduler.controller;

import com.github.embed.scheduler.annotation.JwtAuth;
import com.github.embed.scheduler.dao.TaskUserDao;
import com.github.embed.scheduler.dto.LoginRequest;
import com.github.embed.scheduler.dto.AuthResponse;
import com.github.embed.scheduler.entity.TaskUser;
import com.github.embed.scheduler.service.JwtAuthService;
import com.github.embed.scheduler.util.PasswordUtil; // Will create this next

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.github.embed.scheduler.annotation.JwtAuth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Optional;

@RestController
@RequestMapping("/embed-api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private TaskUserDao taskUserDao;

    @Autowired
    private JwtAuthService jwtAuthService;


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
        String hashedPassword = PasswordUtil.hashPassword(loginRequest.getPassword(),
                loginRequest.getUsername());

        if (!hashedPassword.equals(user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password.");
        }



        final String token = jwtAuthService.generateToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername()));
    }


    @GetMapping("/validate-token")
    public ResponseEntity<?> validateToken() {
        // If the request reaches here, the token is valid (due to JwtAuthInterceptor)
        return ResponseEntity.ok(Collections.singletonMap("valid", true));
    }
}
