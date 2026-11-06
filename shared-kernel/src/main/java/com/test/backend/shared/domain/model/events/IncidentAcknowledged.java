package com.test.backend.shared.domain.model.events;

import java.time.LocalDateTime;

public record IncidentAcknowledged(
        Long incidentId,
        String status,
        LocalDateTime acknowledgedAt
) {
}
