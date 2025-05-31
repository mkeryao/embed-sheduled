package com.example.taskscheduler.notification;

/**
 * Defines the contract for a notification channel.
 * Implementations of this interface are responsible for sending notifications
 * through a specific medium (e.g., webhook, email).
 */
public interface NotificationChannel {
    /** Constant identifying the Webhook notification channel type. */
    String WEBHOOK_CHANNEL_TYPE = "WEBHOOK";
    /** Constant identifying the Email notification channel type (for future use). */
    String EMAIL_CHANNEL_TYPE = "EMAIL";

    /**
     * Gets the unique type identifier for this notification channel.
     * This type is used to match user preferences with available channels.
     * @return A string representing the channel type (e.g., "WEBHOOK").
     */
    String getChannelType();

    /**
     * Sends a notification based on the provided context.
     * The context contains all necessary information like task details,
     * execution logs, and user information.
     *
     * @param context The {@link NotificationContext} containing data for the notification.
     */
    void sendNotification(NotificationContext context);
}
