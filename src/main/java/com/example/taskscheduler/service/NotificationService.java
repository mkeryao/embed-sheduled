package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskUserDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.entity.TaskUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private TaskUserDao taskUserDao;

    @Autowired
    private RestTemplate restTemplate; // Autowire RestTemplate

    @Autowired
    private ObjectMapper objectMapper; // For creating JSON payload

    public void sendNotification(TaskConfig task, TaskExecuteLog logEntry) {
        if (task == null || logEntry == null) {
            logger.warn("TaskConfig or TaskExecuteLog is null, cannot send notification.");
            return;
        }

        String userIdsToNotifyRaw = null;
        boolean isSuccess = "SUCCESS".equals(logEntry.getState());
        boolean isFailure = "FAILED".equals(logEntry.getState()) || "TIMED_OUT".equals(logEntry.getState());
        // Consider if SKIPPED also needs notifications based on one of these lists or a new one.
        // For now, only SUCCESS and FAILED/TIMED_OUT trigger based on their specific lists.

        if (isSuccess && StringUtils.hasText(task.getNotifySuccessUserIds())) {
            userIdsToNotifyRaw = task.getNotifySuccessUserIds();
            logger.info("Task '{}' (ID: {}) completed with status SUCCESS. Notifying success users: [{}]. Log ID: {}",
                    task.getTaskName(), task.getTaskId(), userIdsToNotifyRaw, logEntry.getLogId());
        } else if (isFailure && StringUtils.hasText(task.getNotifyFailedUserIds())) {
            userIdsToNotifyRaw = task.getNotifyFailedUserIds();
            logger.info("Task '{}' (ID: {}) completed with status {}. Notifying failure users: [{}]. Log ID: {}",
                    task.getTaskName(), task.getTaskId(), logEntry.getState(), userIdsToNotifyRaw, logEntry.getLogId());
        } else {
            // No notification needed for this status or no users specified
            return;
        }

        if (!StringUtils.hasText(userIdsToNotifyRaw)) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskName", task.getTaskName());
        payload.put("taskId", task.getTaskId());
        payload.put("status", logEntry.getState());
        payload.put("startTime", logEntry.getStartTime() != null ? logEntry.getStartTime().toString() : null);
        payload.put("endTime", logEntry.getEndTime() != null ? logEntry.getEndTime().toString() : null);
        payload.put("message", logEntry.getExMsg());
        payload.put("instanceId", logEntry.getInstanceId());
        payload.put("logId", logEntry.getLogId());

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.error("Error creating JSON payload for notification (Task ID: {}): {}", task.getTaskId(), e.getMessage());
            return;
        }

        Arrays.stream(userIdsToNotifyRaw.split(","))
            .map(String::trim)
            .filter(idStr -> !idStr.isEmpty())
            .forEach(userIdStr -> {
                try {
                    Integer userId = Integer.parseInt(userIdStr);
                    Optional<TaskUser> userOptional = taskUserDao.findById(userId);
                    if (userOptional.isPresent()) {
                        TaskUser user = userOptional.get();
                        if (StringUtils.hasText(user.getWebhookAddress())) {
                            sendWebhook(user, jsonPayload, task.getTaskName());
                        } else {
                            logger.debug("User {} (ID: {}) has no webhook address configured for task {} notification.", user.getUsername(), userId, task.getTaskName());
                        }
                    } else {
                        logger.warn("User ID {} not found for task {} notification.", userId, task.getTaskName());
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid user ID format '{}' in notification list for task {}.", userIdStr, task.getTaskName());
                }
            });
    }

    private void sendWebhook(TaskUser user, String jsonPayload, String taskName) {
        String webhookUrl = user.getWebhookAddress();
        logger.info("Attempting to send webhook notification for task '{}' to user '{}' at {}", taskName, user.getUsername(), webhookUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForEntity(webhookUrl, entity, String.class); // Or Void.class if no response body expected
            logger.info("Webhook notification sent successfully for task '{}' to user '{}' at {}.", taskName, user.getUsername(), webhookUrl);
        } catch (Exception e) {
            logger.error("Failed to send webhook notification for task '{}' to user '{}' at {}: {}", taskName, user.getUsername(), webhookUrl, e.getMessage());
            // Log more details of the exception if in debug mode or if it's a persistent issue
            // logger.debug("Webhook sending exception details:", e);
        }
    }
}
