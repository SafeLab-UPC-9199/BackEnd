package com.test.backend.alerts.messaging;

import com.test.backend.alerts.application.internal.eventhandlers.TemperatureReadingEventHandler;
import com.test.backend.alerts.domain.services.EvaluateReadingUseCase;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.ProcessedEventRepository;
import com.test.backend.alerts.domain.model.entities.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ADR-005 consumer contract: idempotent (inbox), transactional and fail-fast so
 * unprocessable messages end up in the DLQ instead of being lost in a log line.
 */
class TemperatureReadingEventHandlerTest {

    private static final String THERMAL_READING = """
            {"readingId":42,"laboratoryId":1,"metricTypeId":5,"metricKey":"temperature",
             "value":8.5,"recordedAt":"2026-11-07T10:15:30","minThreshold":2.0,
             "maxThreshold":8.0,"labName":"Lab 4"}""";

    private EvaluateReadingUseCase evaluateReadingUseCase;
    private ProcessedEventRepository processedEventRepository;
    private TemperatureReadingEventHandler handler;

    @BeforeEach
    void setUp() {
        evaluateReadingUseCase = mock(EvaluateReadingUseCase.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        handler = new TemperatureReadingEventHandler(evaluateReadingUseCase, processedEventRepository);
    }

    @Test
    @DisplayName("primera entrega: evalua la lectura y registra la fila del inbox")
    void firstDeliveryEvaluatesAndRegistersInboxRow() {
        handler.onMessage(message("outbox-event-1", THERMAL_READING));

        verify(evaluateReadingUseCase).evaluate(1L, 8.5, 2.0, 8.0, "Lab 4");

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).saveAndFlush(captor.capture());
        assertEquals("outbox-event-1", captor.getValue().getEventId());
        assertEquals("TemperatureReadingRecorded", captor.getValue().getEventType());
        assertEquals("42", captor.getValue().getAggregateId());
    }

    @Test
    @DisplayName("entrega duplicada (mismo messageId): no se vuelve a evaluar")
    void duplicateDeliveryIsSkipped() {
        when(processedEventRepository.existsByEventId("outbox-event-1")).thenReturn(true);

        handler.onMessage(message("outbox-event-1", THERMAL_READING));

        verifyNoInteractions(evaluateReadingUseCase);
        verify(processedEventRepository, never()).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("un fallo de evaluacion se propaga (retry del contenedor -> DLQ), no se traga")
    void evaluationFailuresPropagateToTheListenerContainer() {
        when(evaluateReadingUseCase.evaluate(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> handler.onMessage(message("outbox-event-2", THERMAL_READING)),
                "swallowing the failure would hide the message instead of dead-lettering it");
        // The inbox row must not survive a failed evaluation: the whole
        // transaction rolls back, so the retry (or a DLQ replay) can process it.
        verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("payload corrupto no se procesa ni se registra como procesado")
    void malformedPayloadIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> handler.onMessage(message("outbox-event-3", "{not-a-json")));

        verifyNoInteractions(evaluateReadingUseCase);
        verify(processedEventRepository, never()).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("metricas no termicas: se registran en el inbox pero no se evaluan")
    void nonThermalMetricsAreInboxedButNotEvaluated() {
        var humidity = """
                {"readingId":43,"laboratoryId":1,"metricTypeId":6,"metricKey":"humidity",
                 "value":55.0,"recordedAt":"2026-11-07T10:15:30","minThreshold":null,
                 "maxThreshold":null,"labName":null}""";

        handler.onMessage(message("outbox-event-4", humidity));

        verifyNoInteractions(evaluateReadingUseCase);
        verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("mensaje sin messageId usa readingId como clave de idempotencia")
    void fallsBackToReadingIdWhenMessageIdIsMissing() {
        handler.onMessage(message(null, THERMAL_READING));

        verify(processedEventRepository).existsByEventId("reading-42");
        verify(processedEventRepository, times(1)).saveAndFlush(any(ProcessedEvent.class));
    }

    private Message message(String messageId, String body) {
        var properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setContentType("application/json");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
