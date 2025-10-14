package com.github.embed.scheduler.notification;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.entity.TaskUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link NotificationChannel} that sends notifications via HTTP webhooks.
 * <p>
 * This channel reads webhook URLs from the {@link TaskUser#getWebhookAddress()} field.
 * The field can contain a single URL string or a JSON array of URL strings.
 * If multiple URLs are provided in a JSON array, a notification will be attempted for each URL.
 * </p>
 * <p>
 * The payload sent to the webhook is a JSON representation of task execution details
 * derived from the {@link NotificationContext}. Fastjson is used for JSON serialization.
 * </p>
 */
@Component // Make it a Spring bean
public class WebhookNotificationChannel implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(WebhookNotificationChannel.class);
    // private static final String CHANNEL_TYPE = "WEBHOOK"; // Use constant from interface

    @Autowired
    private RestTemplate restTemplate; // Ensure RestTemplate bean is available

    @Override
    public String getChannelType() {
        return NotificationChannel.WEBHOOK_CHANNEL_TYPE; // Use constant from interface
    }

    @Override
    public void send(NotificationContext context) {
        TaskUser user = context.getUserToNotify();
        TaskConfig taskConfig = context.getTaskConfig();
        TaskExecuteLog logEntry = context.getTaskExecuteLog();

        if (user == null || !StringUtils.hasText(user.getWebhookAddress())) {
            logger.debug("User {} has no webhook address configured or user is null.", user != null ? user.getUsername() : "null");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskName", taskConfig.getTaskName());
        payload.put("taskId", taskConfig.getTaskId());
        payload.put("status", logEntry.getState());
        payload.put("startTime", logEntry.getStartTime() != null ? logEntry.getStartTime().toString() : null);
        payload.put("endTime", logEntry.getEndTime() != null ? logEntry.getEndTime().toString() : null);
        payload.put("message", logEntry.getExMsg());
        payload.put("instanceId", logEntry.getInstanceId());
        payload.put("logId", logEntry.getLogId());
        payload.put("taskPattern", logEntry.getTaskPattern());
        payload.put("parentLogId", logEntry.getParentLogId());
        payload.put("notificationType", context.isSuccessNotification() ? "SUCCESS_NOTIFICATION" : "FAILURE_NOTIFICATION");
        payload.put("user", user.getUsername());


        String jsonPayload = JSON.toJSONString(payload);

        String webhookAddresses = user.getWebhookAddress();
        // Attempt to parse as JSON list if it starts with [ and ends with ]
        if (StringUtils.hasText(webhookAddresses) && webhookAddresses.trim().startsWith("[")
                && webhookAddresses.trim().endsWith("]")) {
            try {
                List<String> urls = JSON.parseObject(webhookAddresses, new TypeReference<List<String>>() {});
                if (urls != null) {
                    for (String url : urls) {
                        if (StringUtils.hasText(url)) {
                            sendSingleWebhook(url, jsonPayload, taskConfig.getTaskName(), user.getUsername());
                        }
                    }
                } else {
                    logger.warn("Parsed webhook address list is null for user {}.", user.getUsername());
                }
            } catch (Exception e) {
                logger.error("Failed to parse webhook address JSON array for user {}: {}. Assuming it might be a single URL.", user.getUsername(), webhookAddresses, e);
                // Fallback: treat as a single URL if parsing fails and it's not obviously a list
                if (!webhookAddresses.trim().startsWith("[")) { // Avoid re-logging if it was clearly intended as a list
                    sendSingleWebhook(webhookAddresses.trim(), jsonPayload, taskConfig.getTaskName(), user.getUsername());
                }
            }
        } else if (StringUtils.hasText(webhookAddresses)) {
            sendSingleWebhook(webhookAddresses.trim(), jsonPayload, taskConfig.getTaskName(), user.getUsername());
        }
    }

    @Override
    public boolean isChannelEnabled(TaskUser user) {
        return  StringUtils.hasText(user.getWebhookAddress());
    }

    private void sendSingleWebhook(String webhookUrl, String jsonPayload, String taskName, String username) {
        logger.info("Attempting to send webhook notification for task '{}' to user '{}' at {}", taskName, username, webhookUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            restTemplate.postForEntity(webhookUrl, entity, String.class);
            logger.info("Webhook notification sent successfully for task '{}' to user '{}' at {}.", taskName, username, webhookUrl);
        } catch (Exception e) {
            logger.error("Failed to send webhook notification for task '{}' to user '{}' at {}: {}", taskName, username, webhookUrl, e.getMessage(), e);
        }
    }
}
