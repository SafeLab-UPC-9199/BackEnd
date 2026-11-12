package com.test.backend.alerts.domain.services.notification;

public record NotificationDispatchReport(
        NotificationResult primaryResult,
        NotificationResult fallbackResult,
        boolean fallbackTriggered,
        long totalLatencyMs,
        boolean allFailuresRecorded
) {
    public boolean isDelivered() {
        return (primaryResult != null && primaryResult.success()) ||
               (fallbackResult != null && fallbackResult.success());
    }
}
