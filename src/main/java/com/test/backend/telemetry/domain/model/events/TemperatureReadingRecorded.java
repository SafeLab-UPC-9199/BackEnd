package com.test.backend.telemetry.domain.model.events;

import java.time.LocalDateTime;

/**
 * Domain event published by the canonical ingestion pipeline after a reading
 * is persisted. Carries the threshold context resolved at ingestion time so
 * consumers (alerts) can evaluate excursions without re-querying the sensor
 * configuration.
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
