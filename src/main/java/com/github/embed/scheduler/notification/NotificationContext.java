package com.github.embed.scheduler.notification;

import com.github.embed.scheduler.entity.TaskConfig;
import com.github.embed.scheduler.entity.TaskExecuteLog;
import com.github.embed.scheduler.entity.TaskUser;

/**
 * Data Transfer Object (DTO) that encapsulates all necessary information
 * for sending a notification. It is passed to {@link NotificationChannel} implementations.
 */
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

    // Getters

    /** @return The configuration of the task. */
    public TaskConfig getTaskConfig() {
        return taskConfig;
    }

    /** @return The execution log entry. */
    public TaskExecuteLog getTaskExecuteLog() {
        return taskExecuteLog;
    }

    /** @return The user to be notified. */
    public TaskUser getUserToNotify() {
        return userToNotify;
    }

    /** @return True if this is for a success event, false otherwise. */
    public boolean isSuccessNotification() {
        return isSuccessNotification;
    }

    // Setters (optional, primarily for internal modification or testing)

    /** Sets the task configuration. */
    public void setTaskConfig(TaskConfig taskConfig) {
        this.taskConfig = taskConfig;
    }

    /** Sets the task execution log. */
    public void setTaskExecuteLog(TaskExecuteLog taskExecuteLog) {
        this.taskExecuteLog = taskExecuteLog;
    }

    /** Sets the user to be notified. */
    public void setUserToNotify(TaskUser userToNotify) {
        this.userToNotify = userToNotify;
    }

    /** Sets the type of notification (success or failure). */
    public void setSuccessNotification(boolean successNotification) {
        isSuccessNotification = successNotification;
    }
}
