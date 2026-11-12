package com.test.backend.alerts.domain.services.notification;

import java.time.LocalDateTime;

public record NotificationMessage(
        String recipient,
        String subject,
        String body,
        String severity,
        Long laboratoryId,
        Long incidentId,
        String correlationId,
        LocalDateTime timestamp
) {
    public NotificationMessage {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be null or empty");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId cannot be null or empty");
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
