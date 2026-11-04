package com.test.backend.telemetry.domain.model.events;

import java.time.LocalDateTime;

/**
 * Published by the canonical ingestion pipeline when a telemetry frame is
 * rejected (unregistered unit, malformed payload, missing metrics, unknown
 * metric key or no laboratory association) so rejections are observable
 * without parsing application logs.
 */
public record ReadingRejected(
        String unit,
        String reason,
        LocalDateTime occurredAt
) {
}
