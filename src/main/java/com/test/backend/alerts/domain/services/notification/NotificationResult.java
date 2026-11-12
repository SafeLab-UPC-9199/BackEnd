package com.test.backend.alerts.domain.services.notification;

import java.time.LocalDateTime;

public record NotificationResult(
        boolean success,
        NotificationChannelType channel,
        long latencyMs,
        String errorMessage,
        LocalDateTime timestamp
) {
    public static NotificationResult ok(NotificationChannelType channel, long latencyMs) {
        return new NotificationResult(true, channel, latencyMs, null, LocalDateTime.now());
    }

    public static NotificationResult failure(NotificationChannelType channel, long latencyMs, String errorMessage) {
        return new NotificationResult(false, channel, latencyMs, errorMessage, LocalDateTime.now());
    }
}
