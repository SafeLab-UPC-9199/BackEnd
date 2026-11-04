package com.test.backend.shared.infrastructure.messaging.outbox;

import com.test.backend.alerts.domain.model.events.OutboxEventPublished;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedEventOutboxDispatcherTest {

    private static final int MAX_RETRIES = 5;
    private static final int PURGE_AFTER_DAYS = 7;

    @Mock
    private SharedEventOutboxRepository sharedEventOutboxRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SharedEventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatcher, "maxRetries", MAX_RETRIES);
        ReflectionTestUtils.setField(dispatcher, "purgeAfterDays", PURGE_AFTER_DAYS);
    }

    private SharedEventOutbox pendingEvent() {
        return new SharedEventOutbox("INCIDENT_CREATED", "Incident", "1",
                "{\"incidentId\":1}", "10", "corr-123", SharedEventOutbox.DEFAULT_SCHEMA_VERSION);
    }

    @Test
    @DisplayName("dispatchPendingEvents publishes envelope with correlationId/schemaVersion and marks PUBLISHED (AAA)")
    void dispatchPendingEvents_ShouldPublishAndMarkPublished_WhenNoError() {
        // Arrange
        var event = pendingEvent();
        when(sharedEventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(sharedEventOutboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        ArgumentCaptor<OutboxEventPublished> captor = ArgumentCaptor.forClass(OutboxEventPublished.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        var published = captor.getValue();
        assertEquals("INCIDENT_CREATED", published.eventType());
        assertEquals("corr-123", published.correlationId());
        assertEquals(SharedEventOutbox.DEFAULT_SCHEMA_VERSION, published.schemaVersion());
        assertEquals(event.getEventId(), published.eventId());
        verify(sharedEventOutboxRepository).save(argThat(e ->
                "PUBLISHED".equals(e.getStatus()) && e.getRetryCount() == 0 && e.getLastError() == null));
    }

    @Test
    @DisplayName("dispatchPendingEvents keeps PENDING and increments retryCount when publish fails (AAA)")
    void dispatchPendingEvents_ShouldRetry_WhenPublishFails() {
        // Arrange
        var event = pendingEvent();
        when(sharedEventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(sharedEventOutboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("broker down"))
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verify(sharedEventOutboxRepository).save(argThat(e ->
                "PENDING".equals(e.getStatus())
                        && e.getRetryCount() == 1
                        && e.getLastError() != null
                        && e.getLastError().contains("broker down")));
    }

    @Test
    @DisplayName("dispatchPendingEvents moves event to FAILED after reaching max retries (AAA)")
    void dispatchPendingEvents_ShouldMarkFailed_WhenMaxRetriesReached() {
        // Arrange: retryCount already at maxRetries - 1, next failure must fail over
        var event = pendingEvent();
        event.setRetryCount(MAX_RETRIES - 1);
        when(sharedEventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(sharedEventOutboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("still down"))
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verify(sharedEventOutboxRepository).save(argThat(e ->
                "FAILED".equals(e.getStatus()) && e.getRetryCount() == MAX_RETRIES));
    }

    @Test
    @DisplayName("dispatchPendingEvents does nothing when there are no pending events (AAA)")
    void dispatchPendingEvents_ShouldDoNothing_WhenNoPendingEvents() {
        // Arrange
        when(sharedEventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of());

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verifyNoInteractions(applicationEventPublisher);
        verify(sharedEventOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("a failing event does not block the rest of the batch (AAA)")
    void dispatchPendingEvents_ShouldContinueBatch_WhenOneEventFails() {
        // Arrange: first publish throws, second succeeds
        var bad = pendingEvent();
        var good = pendingEvent();
        good.setEventId("event-2");
        when(sharedEventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(bad, good));
        when(sharedEventOutboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("boom")).doNothing()
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert: both events processed despite the first failure
        verify(sharedEventOutboxRepository, times(2)).save(any(SharedEventOutbox.class));
        assertEquals(1, bad.getRetryCount());
        assertEquals("PENDING", bad.getStatus());
        assertEquals("PUBLISHED", good.getStatus());
    }

    @Test
    @DisplayName("purgePublishedEvents deletes PUBLISHED entries older than purge-after-days (AAA)")
    void purgePublishedEvents_ShouldDeleteOldPublishedEntries() {
        // Arrange
        when(sharedEventOutboxRepository.deletePublishedBefore(any(LocalDateTime.class))).thenReturn(3);

        // Act
        dispatcher.purgePublishedEvents();

        // Assert
        var cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sharedEventOutboxRepository).deletePublishedBefore(cutoffCaptor.capture());
        var expectedCutoff = LocalDateTime.now().minusDays(PURGE_AFTER_DAYS);
        var actualCutoff = cutoffCaptor.getValue();
        assertTrue(actualCutoff.isAfter(expectedCutoff.minusMinutes(2))
                && actualCutoff.isBefore(expectedCutoff.plusMinutes(2)));
    }
}
