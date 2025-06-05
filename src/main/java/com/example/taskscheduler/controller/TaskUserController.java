package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskUserDao;
import com.example.taskscheduler.dto.UserDto; // Will create this DTO
import com.example.taskscheduler.entity.TaskUser;
import com.example.taskscheduler.util.PasswordUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class TaskUserController {

    @Autowired
    private TaskUserDao taskUserDao;

    @Autowired
    private PasswordUtil passwordUtil;

    // --- DTO Mappers ---
    // --- DTO Mappers ---
    // BeanUtils.copyProperties should handle the new webhookAddress field as names match.
    private UserDto convertToDto(TaskUser user) {
        if (user == null) return null;
        UserDto dto = new UserDto();
        BeanUtils.copyProperties(user, dto);
        dto.setPassword(null); // Never expose password hash in responses
        // Consider if webhookAddress should be masked or partially hidden in response if sensitive
        return dto;
    }

    private TaskUser convertToEntity(UserDto dto) {
        if (dto == null) return null;
        TaskUser user = new TaskUser();
        BeanUtils.copyProperties(dto, user);
        return user;
    }

    // --- API Endpoints ---

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserDto userDto) {
        if (userDto == null || userDto.getUsername() == null || userDto.getUsername().isEmpty() ||
            userDto.getPassword() == null || userDto.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("Username and password are required.");
        }

        if (taskUserDao.findByUsername(userDto.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists.");
        }

        TaskUser user = convertToEntity(userDto);
        user.setUserId(null); // Ensure it's a new user
        // Hash the password before saving
        user.setPasswordHash(passwordUtil.hashPassword(userDto.getPassword(), userDto.getUsername()));

        TaskUser savedUser = taskUserDao.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToDto(savedUser));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<TaskUser> users = taskUserDao.findAll();
        List<UserDto> dtos = users.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Integer id) {
        Optional<TaskUser> userOptional = taskUserDao.findById(id);
        return userOptional.map(user -> ResponseEntity.ok(convertToDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody UserDto userDto) {
        if (userDto == null) return ResponseEntity.badRequest().build();
        Optional<TaskUser> existingUserOptional = taskUserDao.findById(id);
        if (!existingUserOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        TaskUser existingUser = existingUserOptional.get();
        // Update fields from DTO
        if (userDto.getEmail() != null) {
            existingUser.setEmail(userDto.getEmail());
        }
        if (userDto.getUsername() != null && !userDto.getUsername().isEmpty() && !userDto.getUsername().equals(existingUser.getUsername())) {
            // Check if new username is taken
            Optional<TaskUser> userWithNewUsername = taskUserDao.findByUsername(userDto.getUsername());
            if(userWithNewUsername.isPresent() && !userWithNewUsername.get().getUserId().equals(id)){
                 return ResponseEntity.status(HttpStatus.CONFLICT).body("New username '" + userDto.getUsername() + "' already exists.");
            }
            existingUser.setUsername(userDto.getUsername());
        }
        existingUser.setAdmin(userDto.isAdmin()); // Update admin status
        if (userDto.getWebhookAddress() != null) { // Allow unsetting webhook address
            existingUser.setWebhookAddress(userDto.getWebhookAddress().isEmpty() ? null : userDto.getWebhookAddress());
        }
        // Update notificationPreferencesJson
        if (userDto.getNotificationPreferencesJson() != null) {
            existingUser.setNotificationPreferencesJson(userDto.getNotificationPreferencesJson().isEmpty() ? null : userDto.getNotificationPreferencesJson());
        } else {
            // If DTO field is null, explicitly set entity field to null to allow clearing via API
            existingUser.setNotificationPreferencesJson(null);
        }


        // Update password if provided
        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            existingUser.setPasswordHash(passwordUtil.hashPassword(userDto.getPassword(), existingUser.getUsername()));
        }

        int updatedRows = taskUserDao.update(existingUser);
        if (updatedRows == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update user.");
        }
        return ResponseEntity.ok(convertToDto(existingUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        if (!taskUserDao.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        taskUserDao.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
