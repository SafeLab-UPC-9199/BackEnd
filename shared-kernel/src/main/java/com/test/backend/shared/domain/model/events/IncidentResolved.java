package com.test.backend.shared.domain.model.events;

import java.time.LocalDateTime;

public record IncidentResolved(
        Long incidentId,
        String status,
        LocalDateTime resolvedAt
) {
}
