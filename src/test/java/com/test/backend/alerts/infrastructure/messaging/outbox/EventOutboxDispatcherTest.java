package com.test.backend.alerts.infrastructure.messaging.outbox;

import com.test.backend.alerts.domain.model.entities.EventOutbox;
import com.test.backend.alerts.domain.model.events.OutboxEventPublished;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.EventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventOutboxDispatcherTest {

    @Mock
    private EventOutboxRepository eventOutboxRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private EventOutboxDispatcher dispatcher;

    private EventOutbox pendingEvent() {
        return new EventOutbox("INCIDENT_CREATED", "Incident", "1",
                "{\"incidentId\":1}", "10", "corr-123", EventOutbox.DEFAULT_SCHEMA_VERSION);
    }

    @Test
    @DisplayName("dispatchPendingEvents publishes envelope with correlationId/schemaVersion and marks PUBLISHED (AAA)")
    void dispatchPendingEvents_ShouldPublishAndMarkPublished_WhenNoError() {
        // Arrange
        var event = pendingEvent();
        when(eventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(eventOutboxRepository.save(any(EventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        ArgumentCaptor<OutboxEventPublished> captor = ArgumentCaptor.forClass(OutboxEventPublished.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        var published = captor.getValue();
        assertEquals("INCIDENT_CREATED", published.eventType());
        assertEquals("corr-123", published.correlationId());
        assertEquals(EventOutbox.DEFAULT_SCHEMA_VERSION, published.schemaVersion());
        verify(eventOutboxRepository).save(argThat(e ->
                "PUBLISHED".equals(e.getStatus()) && e.getRetryCount() == 0));
    }

    @Test
    @DisplayName("dispatchPendingEvents keeps PENDING and increments retryCount when publish fails (AAA)")
    void dispatchPendingEvents_ShouldRetry_WhenPublishFails() {
        // Arrange
        var event = pendingEvent();
        when(eventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(eventOutboxRepository.save(any(EventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("broker down"))
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verify(eventOutboxRepository).save(argThat(e ->
                "PENDING".equals(e.getStatus())
                        && e.getRetryCount() == 1
                        && e.getLastError() != null
                        && e.getLastError().contains("broker down")));
    }

    @Test
    @DisplayName("dispatchPendingEvents moves event to FAILED after reaching max retries (AAA)")
    void dispatchPendingEvents_ShouldMarkFailed_WhenMaxRetriesReached() {
        // Arrange
        var event = pendingEvent();
        event.setRetryCount(EventOutboxDispatcher.MAX_RETRIES - 1);
        when(eventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(event));
        when(eventOutboxRepository.save(any(EventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("still down"))
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verify(eventOutboxRepository).save(argThat(e ->
                "FAILED".equals(e.getStatus())
                        && e.getRetryCount() == EventOutboxDispatcher.MAX_RETRIES));
    }

    @Test
    @DisplayName("dispatchPendingEvents does nothing when there are no pending events (AAA)")
    void dispatchPendingEvents_ShouldDoNothing_WhenNoPendingEvents() {
        // Arrange
        when(eventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of());

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert
        verifyNoInteractions(applicationEventPublisher);
        verify(eventOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("a failing event does not block the rest of the batch (AAA)")
    void dispatchPendingEvents_ShouldContinueBatch_WhenOneEventFails() {
        // Arrange: first publish throws, second succeeds
        var bad = pendingEvent();
        var good = pendingEvent();
        good.setEventId("event-2");
        when(eventOutboxRepository.findByStatus("PENDING")).thenReturn(List.of(bad, good));
        when(eventOutboxRepository.save(any(EventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("boom")).doNothing()
                .when(applicationEventPublisher).publishEvent(any(OutboxEventPublished.class));

        // Act
        dispatcher.dispatchPendingEvents();

        // Assert: both events processed despite the first failure
        verify(eventOutboxRepository, times(2)).save(any(EventOutbox.class));
        assertEquals(1, bad.getRetryCount());
        assertEquals("PENDING", bad.getStatus());
        assertEquals("PUBLISHED", good.getStatus());
    }
}
