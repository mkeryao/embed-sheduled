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

/**
 * Service responsible for sending notifications based on task execution outcomes.
 * Notifications are sent via webhooks configured for users.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private TaskUserDao taskUserDao;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sends notifications to users based on the task's configuration and execution result.
     * <p>
     * It checks {@link TaskConfig#getNotifySuccessUserIds()} or {@link TaskConfig#getNotifyFailedUserIds()}
     * depending on the {@link TaskExecuteLog#getState()}. If user IDs are specified, it fetches each user's
     * webhook address and sends a JSON payload with task execution details.
     * </p>
     *
     * @param task The configuration of the task that was executed.
     * @param logEntry The execution log entry containing the result of the task.
     */
    public void sendNotification(TaskConfig task, TaskExecuteLog logEntry) {
        if (task == null || logEntry == null) {
            logger.warn("TaskConfig or TaskExecuteLog is null, cannot send notification.");
            return;
        }

        String userIdsToNotifyRaw = null;
        boolean isSuccess = "SUCCESS".equals(logEntry.getState());
        // Consider FAILED, TIMED_OUT, and potentially SKIPPED (if configured) as failure conditions for notification
        boolean isFailureOrTimeout = "FAILED".equals(logEntry.getState()) || "TIMED_OUT".equals(logEntry.getState());
        // boolean isSkipped = "SKIPPED".equals(logEntry.getState()); // Example if SKIPPED needs notifications

        if (isSuccess && StringUtils.hasText(task.getNotifySuccessUserIds())) {
            userIdsToNotifyRaw = task.getNotifySuccessUserIds();
            logger.info("Task '{}' (ID: {}) completed with status SUCCESS. Notifying success users: [{}]. Log ID: {}",
                    task.getTaskName(), task.getTaskId(), userIdsToNotifyRaw, logEntry.getLogId());
        } else if (isFailureOrTimeout && StringUtils.hasText(task.getNotifyFailedUserIds())) {
            userIdsToNotifyRaw = task.getNotifyFailedUserIds();
            logger.info("Task '{}' (ID: {}) completed with status {}. Notifying failure users: [{}]. Log ID: {}",
                    task.getTaskName(), task.getTaskId(), logEntry.getState(), userIdsToNotifyRaw, logEntry.getLogId());
        } else {
            logger.debug("No notification required for task '{}' (ID: {}) with status {} or no users specified for this outcome. Log ID: {}",
                 task.getTaskName(), task.getTaskId(), logEntry.getState(), logEntry.getLogId());
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
        payload.put("message", logEntry.getExMsg()); // This is often the exception message for failures
        payload.put("instanceId", logEntry.getInstanceId());
        payload.put("logId", logEntry.getLogId());
        payload.put("taskPattern", logEntry.getTaskPattern());
        payload.put("parentLogId", logEntry.getParentLogId());


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

    /**
     * Sends the JSON payload to the user's configured webhook address.
     *
     * @param user The user to notify, containing the webhook address.
     * @param jsonPayload The JSON string payload to send.
     * @param taskName The name of the task for logging purposes.
     */
    private void sendWebhook(TaskUser user, String jsonPayload, String taskName) {
        String webhookUrl = user.getWebhookAddress();
        logger.info("Attempting to send webhook notification for task '{}' to user '{}' at {}", taskName, user.getUsername(), webhookUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            // Consider adding timeout configurations for RestTemplate
            restTemplate.postForEntity(webhookUrl, entity, String.class); 
            logger.info("Webhook notification sent successfully for task '{}' to user '{}' at {}.", taskName, user.getUsername(), webhookUrl);
        } catch (Exception e) {
            logger.error("Failed to send webhook notification for task '{}' to user '{}' at {}: {}", taskName, user.getUsername(), webhookUrl, e.getMessage());
            // For persistent errors, more specific error handling or retry logic might be needed.
        }
    }
}
