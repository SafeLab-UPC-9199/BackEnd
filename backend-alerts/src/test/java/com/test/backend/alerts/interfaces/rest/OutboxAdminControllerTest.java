package com.test.backend.alerts.interfaces.rest;

import com.test.backend.alerts.application.internal.eventhandlers.OutboxEventPublishedHandler;
import com.test.backend.shared.application.CurrentWorkspaceService;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxAdminControllerTest {

    @Mock
    private SharedEventOutboxRepository sharedEventOutboxRepository;

    @Mock
    private OutboxEventPublishedHandler outboxEventPublishedHandler;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private OutboxAdminController controller;

    @BeforeEach
    void defaults() {
        // non-admin user of workspace 10 (lenient: each test only consumes what it needs)
        lenient().when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        lenient().when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
    }

    private void asAdmin() {
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("retryFailed re-queues a FAILED event and returns 202 when caller is admin (AAA)")
    void retryFailed_ShouldReturnAccepted_WhenEventIsRequeued() {
        // Arrange
        asAdmin();
        when(sharedEventOutboxRepository.requeueFailedById(7L)).thenReturn(1);

        // Act
        var response = controller.retryFailed(7L);

        // Assert
        assertEquals(202, response.getStatusCode().value());
        assertEquals("REQUEUED", response.getBody().get("status"));
        verify(sharedEventOutboxRepository).requeueFailedById(7L);
    }

    @Test
    @DisplayName("retryFailed returns 403 for non-administrators (QAS-05)")
    void retryFailed_ShouldReturnForbidden_ForNonAdmin() {
        // Act
        var response = controller.retryFailed(7L);

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verify(sharedEventOutboxRepository, never()).requeueFailedById(any());
    }

    @Test
    @DisplayName("retryFailed returns 404 when the event does not exist or is not FAILED (AAA)")
    void retryFailed_ShouldReturnNotFound_WhenNothingRequeued() {
        // Arrange
        asAdmin();
        when(sharedEventOutboxRepository.requeueFailedById(99L)).thenReturn(0);

        // Act
        var response = controller.retryFailed(99L);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("retryAllFailed re-queues every FAILED event for admins and reports the count (AAA)")
    void retryAllFailed_ShouldReportRequeuedCount() {
        // Arrange
        asAdmin();
        when(sharedEventOutboxRepository.requeueAllFailed()).thenReturn(4);

        // Act
        var response = controller.retryAllFailed();

        // Assert
        assertEquals(202, response.getStatusCode().value());
        assertEquals("REQUEUED", response.getBody().get("status"));
        assertEquals(4, response.getBody().get("requeued"));
    }

    @Test
    @DisplayName("retryAllFailed returns 403 for non-administrators (QAS-05)")
    void retryAllFailed_ShouldReturnForbidden_ForNonAdmin() {
        // Act
        var response = controller.retryAllFailed();

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verify(sharedEventOutboxRepository, never()).requeueAllFailed();
    }

    @Test
    @DisplayName("stats includes the delivered counter and only counts the caller's workspace events (QAS-05)")
    void stats_ShouldBeWorkspaceScoped() {
        // Arrange - only org 10 belongs to the caller
        var own = new SharedEventOutbox("INCIDENT_CREATED", "Incident", "1", "{}", "10");
        own.setStatus("PENDING");
        var foreign = new SharedEventOutbox("INCIDENT_CREATED", "Incident", "2", "{}", "99");
        foreign.setStatus("FAILED");
        when(sharedEventOutboxRepository.findAll()).thenReturn(List.of(own, foreign));
        when(outboxEventPublishedHandler.getDeliveredCount()).thenReturn(12L);

        // Act
        var response = controller.stats();

        // Assert
        assertEquals(200, response.getStatusCode().value());
        var stats = response.getBody();
        assertEquals(1L, stats.get("pending"));
        assertEquals(0L, stats.get("failed"), "foreign workspace events must not be counted");
        assertEquals(12L, stats.get("deliveredSinceStartup"));
    }

    @Test
    @DisplayName("failed lists only FAILED entries of the caller's workspace (QAS-05)")
    void failed_ShouldListOnlyFailedEntriesOfOwnWorkspace() {
        // Arrange
        var ok = new SharedEventOutbox("INCIDENT_CREATED", "Incident", "1", "{}", "10");
        ok.setStatus("PUBLISHED");
        var bad = new SharedEventOutbox("INCIDENT_RESOLVED", "Incident", "2", "{}", "10");
        bad.setStatus("FAILED");
        bad.setLastError("broker down");
        var foreignFailed = new SharedEventOutbox("INCIDENT_RESOLVED", "Incident", "3", "{}", "99");
        foreignFailed.setStatus("FAILED");
        when(sharedEventOutboxRepository.findAll()).thenReturn(List.of(ok, bad, foreignFailed));

        // Act
        var response = controller.failed(50);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        var failed = response.getBody();
        assertEquals(1, failed.size(), "foreign workspace events must not be listed");
        assertEquals("INCIDENT_RESOLVED", failed.get(0).get("eventType"));
        assertEquals("broker down", failed.get(0).get("lastError"));
    }

    @Test
    @DisplayName("admins see failed events of every workspace (QAS-05)")
    void failed_ShouldIncludeForeignWorkspaces_ForAdmin() {
        // Arrange
        asAdmin();
        var foreignFailed = new SharedEventOutbox("INCIDENT_RESOLVED", "Incident", "3", "{}", "99");
        foreignFailed.setStatus("FAILED");
        when(sharedEventOutboxRepository.findAll()).thenReturn(List.of(foreignFailed));

        // Act
        var response = controller.failed(50);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }
}
