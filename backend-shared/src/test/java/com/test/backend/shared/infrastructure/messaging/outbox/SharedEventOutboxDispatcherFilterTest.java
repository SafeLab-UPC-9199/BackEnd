package com.test.backend.shared.infrastructure.messaging.outbox;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The outbox is a single table shared by every microservice, so the dispatcher
 * must only drain the event types it owns (ADR-005): otherwise the shared
 * service would mark telemetry's readings PUBLISHED without ever handing them
 * to the broker. These are the guards that make "one owner per event type"
 * hold, plus the retry contract when the publisher fails.
 */
class SharedEventOutboxDispatcherFilterTest {

    private SharedEventOutboxRepository repository;
    private ApplicationEventPublisher publisher;
    private SharedEventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(SharedEventOutboxRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        dispatcher = new SharedEventOutboxDispatcher(repository, publisher);
        ReflectionTestUtils.setField(dispatcher, "maxRetries", 5);
        ReflectionTestUtils.setField(dispatcher, "purgeAfterDays", 7);
    }

    @Test
    @DisplayName("con outbox.dispatch-event-types solo se drenan los tipos propios")
    void dispatchesOnlyOwnedEventTypes() {
        ReflectionTestUtils.setField(dispatcher, "dispatchEventTypes", "TemperatureReadingRecorded");
        var reading = outbox("TemperatureReadingRecorded");
        var incident = outbox("INCIDENT_CREATED");
        when(repository.findByStatus("PENDING")).thenReturn(List.of(reading, incident));

        dispatcher.dispatchPendingEvents();

        verify(publisher, times(1)).publishEvent(reading);
        verify(publisher, never()).publishEvent(incident);
        assertEquals("PUBLISHED", reading.getStatus());
        assertEquals("PENDING", incident.getStatus(), "rows of other services must stay untouched");
        verify(repository, times(1)).save(reading);
    }

    @Test
    @DisplayName("sin el filtro (default vacio) se drenan todos los tipos: comportamiento original")
    void dispatchesEverythingWhenFilterIsEmpty() {
        ReflectionTestUtils.setField(dispatcher, "dispatchEventTypes", "");
        var incident = outbox("INCIDENT_CREATED");
        when(repository.findByStatus("PENDING")).thenReturn(List.of(incident));

        dispatcher.dispatchPendingEvents();

        verify(publisher).publishEvent(incident);
        assertEquals("PUBLISHED", incident.getStatus());
    }

    @Test
    @DisplayName("si el publicador falla la fila queda PENDING para el siguiente reintento")
    void failedPublishKeepsRowPendingForRetry() {
        ReflectionTestUtils.setField(dispatcher, "dispatchEventTypes", "TemperatureReadingRecorded");
        var reading = outbox("TemperatureReadingRecorded");
        when(repository.findByStatus("PENDING")).thenReturn(List.of(reading));
        doThrow(new RuntimeException("broker down")).when(publisher).publishEvent(any(Object.class));

        dispatcher.dispatchPendingEvents();

        assertEquals("PENDING", reading.getStatus(), "a broker outage must never mark the event as delivered");
        assertEquals(1, reading.getRetryCount());
    }

    private SharedEventOutbox outbox(String eventType) {
        return new SharedEventOutbox(eventType, "SensorReading", "1", "{}", null);
    }
}
