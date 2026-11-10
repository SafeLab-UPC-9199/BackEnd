package com.test.backend.alerts.interfaces.rest;

import com.test.backend.alerts.application.internal.eventhandlers.OutboxEventPublishedHandler;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * QAS-05 (monolith parity): outbox listings are workspace-filtered for
 * non-administrators and DLQ replay is Administrator-only.
 */
class OutboxAdminControllerTest {

    private SharedEventOutboxRepository repository;
    private OutboxAdminController controller;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SharedEventOutboxRepository.class);
        var handler = Mockito.mock(OutboxEventPublishedHandler.class);
        controller = new OutboxAdminController(repository, handler);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void withIdentity(String roles, String workspaceId) {
        var request = new MockHttpServletRequest();
        if (roles != null) {
            request.addHeader("X-User-Roles", roles);
        }
        if (workspaceId != null) {
            request.addHeader("X-Workspace-Id", workspaceId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private SharedEventOutbox failedEvent(String organizationId) {
        var event = new SharedEventOutbox("TemperatureReadingRecorded", "Sensor", "1", "{}", organizationId);
        event.setStatus("FAILED");
        event.setRetryCount(3);
        event.setLastError("broker down");
        return event;
    }

    @Test
    void readsWithoutIdentityAreForbidden() {
        withIdentity(null, null);

        assertEquals(HttpStatus.FORBIDDEN, controller.stats().getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.failed(50).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void nonAdminWithoutWorkspaceCannotRead() {
        withIdentity("ROLE_USER", null);

        assertEquals(HttpStatus.FORBIDDEN, controller.stats().getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void nonAdminOnlySeesOwnWorkspaceEvents() {
        withIdentity("ROLE_USER", "1");
        var own = failedEvent("1");
        var foreign = failedEvent("2");
        Mockito.when(repository.findAll()).thenReturn(List.of(own, foreign));

        var stats = controller.stats();
        var failed = controller.failed(50);

        assertEquals(HttpStatus.OK, stats.getStatusCode());
        assertEquals(1L, stats.getBody().get("failed"));
        assertEquals(1, failed.getBody().size());
        assertEquals(own.getEventId(), failed.getBody().get(0).get("eventId"));
    }

    @Test
    void adminSeesEveryWorkspace() {
        withIdentity("ROLE_ADMIN", null);
        Mockito.when(repository.findAll()).thenReturn(List.of(failedEvent("1"), failedEvent("2")));

        var stats = controller.stats();

        assertEquals(HttpStatus.OK, stats.getStatusCode());
        assertEquals(2L, stats.getBody().get("failed"));
        assertEquals(2, controller.failed(50).getBody().size());
    }

    @Test
    void dlqReplayIsForbiddenForNonAdmin() {
        withIdentity("ROLE_USER", "1");

        assertEquals(HttpStatus.FORBIDDEN, controller.retryFailed(1L).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.retryAllFailed().getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void dlqReplayWithoutIdentityIsForbidden() {
        withIdentity(null, null);

        assertEquals(HttpStatus.FORBIDDEN, controller.retryAllFailed().getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void adminReplayPassesTheGate() {
        withIdentity("ROLE_ADMIN", null);
        Mockito.when(repository.findAll()).thenReturn(List.of());
        Mockito.when(repository.requeueFailedById(99L)).thenReturn(0);
        Mockito.when(repository.requeueAllFailed()).thenReturn(0);

        // Gate passed: unknown id -> 404, replay-all -> 202.
        assertEquals(HttpStatus.NOT_FOUND, controller.retryFailed(99L).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, controller.retryAllFailed().getStatusCode());
        verify(repository).requeueAllFailed();
        assertTrue(controller.stats().getStatusCode().is2xxSuccessful());
    }
}
