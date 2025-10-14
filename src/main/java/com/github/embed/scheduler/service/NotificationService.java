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
     * Sends a success notification for a completed task.
     *
     * @param taskConfig The configuration of the task.
     * @param logEntry The execution log.
     */
    public void sendSuccessNotification(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        if (taskConfig == null || logEntry == null) {
            logger.warn("TaskConfig or TaskExecuteLog is null, cannot send success notification.");
            return;
        }
        sendNotification(taskConfig, logEntry, taskConfig.getNotifySuccessUserIds(), "SUCCESS");
    }

    /**
     * Sends a failure notification for a completed task.
     *
     * @param taskConfig The configuration of the task.
     * @param logEntry The execution log.
     */
    public void sendFailureNotification(TaskConfig taskConfig, TaskExecuteLog logEntry) {
        if (taskConfig == null || logEntry == null) {
            logger.warn("TaskConfig or TaskExecuteLog is null, cannot send failure notification.");
            return;
        }
        sendNotification(taskConfig, logEntry, taskConfig.getNotifyFailedUserIds(), "FAILURE");
    }

    private void sendNotification(TaskConfig taskConfig, TaskExecuteLog logEntry, String userIdsToNotifyRaw, String notificationType) {
        if (!StringUtils.hasText(userIdsToNotifyRaw)) {
            logger.debug("No users configured for {} notification for task '{}'.", notificationType, taskConfig.getTaskName());
            return;
        }

        logger.info("Processing {} notification for task '{}' (ID: {}) for users [{}]. Log ID: {}",
                notificationType, taskConfig.getTaskName(), taskConfig.getTaskId(), userIdsToNotifyRaw, logEntry.getId());

        if (notificationChannels == null || notificationChannels.isEmpty()) {
            logger.warn("No notification channels are configured. Cannot send notifications.");
            return;
        }
        
        List<Integer> userIds = Arrays.stream(userIdsToNotifyRaw.split(","))
                                      .map(String::trim)
                                      .map(Integer::parseInt)
                                      .collect(java.util.stream.Collectors.toList());

        List<TaskUser> usersToNotify = taskUserDao.findByIds(userIds);

        for (TaskUser user : usersToNotify) {
            NotificationContext context = new NotificationContext(
                user,
                taskConfig,
                logEntry,
                "SUCCESS".equals(notificationType)
            );

            for (NotificationChannel channel : notificationChannels) {
                try {
                    channel.send(context);
                } catch (Exception e) {
                    logger.error("Failed to send notification via channel {} for user {}. Task: {}, Log: {}",
                                 channel.getChannelType(), user.getUserId(), taskConfig.getTaskId(), logEntry.getId(), e);
                }
            }
        }
    }
}
