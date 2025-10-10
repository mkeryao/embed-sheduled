package com.github.embed.scheduler.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.embed.scheduler.dao.TaskUserDao;
import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.entity.TaskUser;
import com.github.embed.scheduler.notification.NotificationChannel;
import com.github.embed.scheduler.notification.NotificationContext;

/**
 * Service responsible for orchestrating notifications based on task execution outcomes.
 * <p>
 * This service determines which users to notify based on task configuration
 * ({@link TaskConfig#getNotifySuccessUserIds()} or {@link TaskConfig#getNotifyFailedUserIds()})
 * and the execution result. It then iterates through available {@link NotificationChannel}
 * implementations (e.g., Webhook, Email) and dispatches the notification if the user
 * has appropriate preferences for that channel.
 * </p>
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private TaskUserDao taskUserDao;

    private final List<NotificationChannel> notificationChannels;

    /**
     * Constructs the NotificationService with a list of available notification channels.
     * Spring's dependency injection provides all beans that implement {@link NotificationChannel}.
     *
     * @param notificationChannels A list of discovered notification channel implementations.
     */
    public NotificationService(List<NotificationChannel> notificationChannels) {
        this.notificationChannels = notificationChannels;
        if (notificationChannels != null) {
            logger.info("NotificationService initialized with {} notification channel(s):", notificationChannels.size());
            for(NotificationChannel channel : notificationChannels) {
                logger.info("- Channel Type: {}", channel.getChannelType());
            }
        } else {
            logger.warn("NotificationService initialized with no notification channels.");
        }
    }

    /**
     * Sends notifications to users based on the task's configuration and execution result.
     * <p>
     * It identifies target users from {@link TaskConfig#getNotifySuccessUserIds()} or
     * {@link TaskConfig#getNotifyFailedUserIds()} based on the {@link TaskExecuteLog#getState()}.
     * For each user, it creates a {@link NotificationContext} and iterates through the
     * available {@link NotificationChannel}s. A notification is sent via a channel if the user
     * has preferences matching that channel type (e.g., a webhook URL for the WEBHOOK channel).
     * </p>
     *
     * @param taskConfig The configuration of the task that was executed.
     * @param logEntry The execution log entry containing the result of the task.
     */
    public void sendNotification(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        if (taskConfig == null || logEntry == null) {
            logger.warn("TaskConfig or TaskExecuteLog is null, cannot send notification.");
            return;
        }

        String userIdsToNotifyRaw = null;
        boolean isSuccessNotification = "SUCCESS".equals(logEntry.getState());
        boolean isFailureNotification = "FAILED".equals(logEntry.getState()) || "TIMED_OUT".equals(logEntry.getState());

        if (isSuccessNotification && StringUtils.hasText(taskConfig.getNotifySuccessUserIds())) {
            userIdsToNotifyRaw = taskConfig.getNotifySuccessUserIds();
            logger.info("Task '{}' (ID: {}) completed with status SUCCESS. Processing success notifications for users: [{}]. Log ID: {}",
                    taskConfig.getTaskName(), taskConfig.getTaskId(), userIdsToNotifyRaw, logEntry.getLogId());
        } else if (isFailureNotification && StringUtils.hasText(taskConfig.getNotifyFailedUserIds())) {
            userIdsToNotifyRaw = taskConfig.getNotifyFailedUserIds();
            logger.info("Task '{}' (ID: {}) completed with status {}. Processing failure notifications for users: [{}]. Log ID: {}",
                    taskConfig.getTaskName(), taskConfig.getTaskId(), logEntry.getState(), userIdsToNotifyRaw, logEntry.getLogId());
        } else {
            logger.debug("No notification required for task '{}' (ID: {}) with status {} or no users specified for this outcome. Log ID: {}",
                 taskConfig.getTaskName(), taskConfig.getTaskId(), logEntry.getState(), logEntry.getLogId());
            return;
        }

        if (!StringUtils.hasText(userIdsToNotifyRaw)) {
            logger.debug("User IDs string is empty for task '{}', no notifications will be sent.", taskConfig.getTaskName());
            return;
        }

        if (notificationChannels == null || notificationChannels.isEmpty()) {
            logger.warn("No notification channels are configured. Cannot send notifications for task '{}'.", taskConfig.getTaskName());
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
                        NotificationContext context = new NotificationContext(taskConfig, logEntry, user, isSuccessNotification);

                        logger.debug("Processing notifications for user '{}' (ID: {}) for task '{}' (Status: {})",
                                     user.getUsername(), userId, taskConfig.getTaskName(), logEntry.getState());

                        boolean notificationSentForUser = false;
                        for (NotificationChannel channel : notificationChannels) {
                            // Basic check: if it's a WEBHOOK channel, does the user have a webhook address?
                            // This will be expanded with UserNotificationPreference checks in the future.
                            if (NotificationChannel.WEBHOOK_CHANNEL_TYPE.equals(channel.getChannelType())) { // Assuming WEBHOOK_CHANNEL_TYPE is defined or use "WEBHOOK" string
                                if (StringUtils.hasText(user.getWebhookAddress())) {
                                    logger.info("Attempting to send notification via channel '{}' for user '{}' for task '{}'",
                                                channel.getChannelType(), user.getUsername(), taskConfig.getTaskName());
                                    channel.sendNotification(context);
                                    notificationSentForUser = true;
                                } else {
                                    logger.debug("User '{}' has no webhook address configured; skipping WEBHOOK channel for task '{}'.",
                                                 user.getUsername(), taskConfig.getTaskName());
                                }
                            } else {
                                // For other channel types (e.g., EMAIL), we'd need specific preference checks.
                                // For now, this example doesn't implement other channels or detailed preferences.
                                logger.debug("Channel type '{}' not yet fully supported with user preferences for user '{}', task '{}'.",
                                             channel.getChannelType(), user.getUsername(), taskConfig.getTaskName());
                            }
                        }
                        if (!notificationSentForUser) {
                             logger.info("No suitable notification channels found or configured for user '{}' for task '{}'.",
                                         user.getUsername(), taskConfig.getTaskName());
                        }

                    } else {
                        logger.warn("User ID {} not found for task {} notification.", userId, taskConfig.getTaskName());
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid user ID format '{}' in notification list for task {}.", userIdStr, taskConfig.getTaskName());
                } catch (Exception e) {
                    logger.error("Error processing notification for user ID '{}', task '{}': {}", userIdStr, taskConfig.getTaskName(), e.getMessage(), e);
                }
            });
    }

    /**
     * A convenience method to specifically send failure notifications.
     * It internally calls {@link #sendNotification(TaskConfig, TaskExecuteLog)}.
     *
     * @param taskConfig The configuration of the task that failed.
     * @param logEntry The execution log entry for the failed task.
     */
    public void sendFailureNotification(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        logger.debug("Dispatching failure notification for task: {}", taskConfig.getTaskName());
        sendNotification(taskConfig, logEntry);
    }

    /**
     * A convenience method to specifically send success notifications.
     * It internally calls {@link #sendNotification(TaskConfig, TaskExecuteLog)}.
     *
     * @param taskConfig The configuration of the task that succeeded.
     * @param logEntry The execution log entry for the successful task.
     */
    public void sendSuccessNotification(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        logger.debug("Dispatching success notification for task: {}", taskConfig.getTaskName());
        sendNotification(taskConfig, logEntry);
    }
}
