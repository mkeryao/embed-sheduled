package com.github.embed.scheduler.notification;

import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.entity.TaskUser;
import lombok.Data;

/**
 * Data Transfer Object (DTO) that encapsulates all necessary information
 * for sending a notification. It is passed to {@link NotificationChannel} implementations.
 */
@Data
public class NotificationContext {
    /** The configuration of the task that triggered the notification. */
    private TaskConfig taskConfig;
    /** The execution log entry associated with this notification event. */
    private TaskExecuteLog taskExecuteLog;
    /** The user to whom the notification should be sent. */
    private TaskUser userToNotify;
    /** Indicates if this is a success (true) or failure/timeout (false) notification. */
    private boolean isSuccessNotification;

    /**
     * Constructs a new NotificationContext.
     *
     * @param taskConfig The task configuration.
     * @param taskExecuteLog The task execution log.
     * @param userToNotify The user to be notified.
     * @param isSuccessNotification True for success notifications, false for failure/timeout.
     */
    public NotificationContext(TaskConfig taskConfig, TaskExecuteLog taskExecuteLog, TaskUser userToNotify, boolean isSuccessNotification) {
        this.taskConfig = taskConfig;
        this.taskExecuteLog = taskExecuteLog;
        this.userToNotify = userToNotify;
        this.isSuccessNotification = isSuccessNotification;
    }

    public NotificationContext(){

    }

}
