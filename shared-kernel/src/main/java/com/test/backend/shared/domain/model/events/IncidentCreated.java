package com.test.backend.shared.domain.model.events;

import java.time.LocalDateTime;

public record IncidentCreated(
        Long incidentId,
        Long laboratoryId,
        String title,
        String severity,
        LocalDateTime createdAt
) {
}
