package com.test.backend.shared.domain.model.events;

import java.time.LocalDateTime;

/**
 * Domain event published when a temperature reading is recorded.
 * Contains all fields needed by downstream contexts (alerts, labs, etc.)
 * without requiring direct service-to-service calls.
 */
public record TemperatureReadingRecorded(
        Long readingId,
        Long laboratoryId,
        Long metricTypeId,
        String metricKey,
        Double value,
        LocalDateTime recordedAt,
        Double minThreshold,
        Double maxThreshold,
        String labName
) {
}
