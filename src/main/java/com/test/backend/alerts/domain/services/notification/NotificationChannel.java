package com.test.backend.alerts.domain.services.notification;

public interface NotificationChannel {
    NotificationChannelType getChannelType();
    NotificationResult send(NotificationMessage message);
}
