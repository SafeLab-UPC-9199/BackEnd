package com.test.backend.shared.domain.model.events;

import java.time.LocalDateTime;

public record IncidentEscalated(
        Long incidentId,
        String escalatedToRole,
        LocalDateTime escalatedAt
) {
}
