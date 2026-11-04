package com.test.backend.alerts.interfaces.rest;

import com.test.backend.alerts.application.internal.eventhandlers.OutboxEventPublishedHandler;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxAdminControllerTest {

    @Mock
    private SharedEventOutboxRepository sharedEventOutboxRepository;

    @Mock
    private OutboxEventPublishedHandler outboxEventPublishedHandler;

    @InjectMocks
    private OutboxAdminController controller;

    @Test
    @DisplayName("retryFailed re-queues a FAILED event and returns 202 (AAA)")
    void retryFailed_ShouldReturnAccepted_WhenEventIsRequeued() {
        // Arrange
        when(sharedEventOutboxRepository.requeueFailedById(7L)).thenReturn(1);

        // Act
        var response = controller.retryFailed(7L);

        // Assert
        assertEquals(202, response.getStatusCode().value());
        assertEquals("REQUEUED", response.getBody().get("status"));
        verify(sharedEventOutboxRepository).requeueFailedById(7L);
    }

    @Test
    @DisplayName("retryFailed returns 404 when the event does not exist or is not FAILED (AAA)")
    void retryFailed_ShouldReturnNotFound_WhenNothingRequeued() {
        // Arrange
        when(sharedEventOutboxRepository.requeueFailedById(99L)).thenReturn(0);

        // Act
        var response = controller.retryFailed(99L);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("retryAllFailed re-queues every FAILED event and reports the count (AAA)")
    void retryAllFailed_ShouldReportRequeuedCount() {
        // Arrange
        when(sharedEventOutboxRepository.requeueAllFailed()).thenReturn(4);

        // Act
        var response = controller.retryAllFailed();

        // Assert
        assertEquals(202, response.getStatusCode().value());
        assertEquals("REQUEUED", response.getBody().get("status"));
        assertEquals(4, response.getBody().get("requeued"));
    }

    @Test
    @DisplayName("stats includes the delivered counter maintained by the outbox listener (AAA)")
    void stats_ShouldIncludeDeliveredCount() {
        // Arrange
        when(sharedEventOutboxRepository.findAll()).thenReturn(List.of());
        when(outboxEventPublishedHandler.getDeliveredCount()).thenReturn(12L);

        // Act
        var stats = controller.stats();

        // Assert
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("published"));
        assertEquals(0L, stats.get("failed"));
        assertEquals(12L, stats.get("deliveredSinceStartup"));
    }

    @Test
    @DisplayName("failed lists only FAILED entries (AAA)")
    void failed_ShouldListOnlyFailedEntries() {
        // Arrange
        var ok = new SharedEventOutbox("INCIDENT_CREATED", "Incident", "1", "{}", "10");
        ok.setStatus("PUBLISHED");
        var bad = new SharedEventOutbox("INCIDENT_RESOLVED", "Incident", "2", "{}", "10");
        bad.setStatus("FAILED");
        bad.setLastError("broker down");
        when(sharedEventOutboxRepository.findAll()).thenReturn(List.of(ok, bad));

        // Act
        var failed = controller.failed(50);

        // Assert
        assertEquals(1, failed.size());
        assertEquals("INCIDENT_RESOLVED", failed.get(0).get("eventType"));
        assertEquals("broker down", failed.get(0).get("lastError"));
    }
}
