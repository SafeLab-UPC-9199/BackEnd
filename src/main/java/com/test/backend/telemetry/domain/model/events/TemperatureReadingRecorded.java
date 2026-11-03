package com.test.backend.telemetry.domain.model.events;

import java.time.LocalDateTime;

public record TemperatureReadingRecorded(
        Long readingId,
        Long laboratoryId,
        Long metricTypeId,
        Double value,
        LocalDateTime recordedAt
) {
}
