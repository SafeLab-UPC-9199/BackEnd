package com.test.backend.alerts.domain.model.events;

import java.time.LocalDateTime;

/**
 * Domain event published by the outbox dispatcher once a pending outbox entry
 * is picked up for delivery. Carries the full envelope (including correlationId
 * and schemaVersion) so consumers can trace and validate the event.
 */
public record OutboxEventPublished(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        String correlationId,
        String schemaVersion,
        String organizationId,
        LocalDateTime occurredAt
) {
}
