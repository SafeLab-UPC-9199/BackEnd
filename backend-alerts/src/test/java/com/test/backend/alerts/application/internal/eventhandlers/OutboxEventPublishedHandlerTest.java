package com.test.backend.alerts.application.internal.eventhandlers;

import com.test.backend.alerts.domain.model.events.OutboxEventPublished;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventPublishedHandlerTest {

    private final OutboxEventPublishedHandler handler = new OutboxEventPublishedHandler();

    private OutboxEventPublished event(String eventId) {
        return new OutboxEventPublished(eventId, "INCIDENT_CREATED", "Incident", "1",
                "{\"incidentId\":1}", "corr-1", "1.0", "10", LocalDateTime.now());
    }

    @Test
    @DisplayName("counts delivered outbox events without throwing (AAA)")
    void on_ShouldIncrementDeliveredCount() {
        // Arrange
        assertEquals(0L, handler.getDeliveredCount());

        // Act
        handler.on(event("event-1"));
        handler.on(event("event-2"));

        // Assert
        assertEquals(2L, handler.getDeliveredCount());
    }
}
