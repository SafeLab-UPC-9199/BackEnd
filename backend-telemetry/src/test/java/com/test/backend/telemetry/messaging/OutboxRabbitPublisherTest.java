package com.test.backend.telemetry.messaging;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.telemetry.infrastructure.messaging.OutboxRabbitPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ADR-005 producer: the bridge from the shared outbox to RabbitMQ.
 */
class OutboxRabbitPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private OutboxRabbitPublisher publisher;
    private SharedEventOutbox readingEvent;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new OutboxRabbitPublisher(rabbitTemplate);
        readingEvent = new SharedEventOutbox(
                MqTopology.TEMPERATURE_READING_EVENT_TYPE, "SensorReading", "42",
                "{\"readingId\":42,\"metricKey\":\"temperature\"}", null);
    }

    @Test
    @DisplayName("publica en el exchange de eventos con messageId = eventId y payload JSON")
    void publishesReadingToTopicExchangeWithOutboxEventId() {
        publisher.onOutboxEvent(readingEvent);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(MqTopology.EVENTS_EXCHANGE),
                eq(MqTopology.TEMPERATURE_READING_ROUTING_KEY),
                captor.capture());

        Message sent = captor.getValue();
        // The consumer uses messageId as idempotency key, so it must be the outbox eventId.
        assertEquals(readingEvent.getEventId(), sent.getMessageProperties().getMessageId());
        assertEquals(readingEvent.getCorrelationId(), sent.getMessageProperties().getCorrelationId());
        assertEquals("application/json", sent.getMessageProperties().getContentType());
        // Persistent delivery + durable queue = messages survive a broker restart.
        assertEquals(MessageDeliveryMode.PERSISTENT, sent.getMessageProperties().getDeliveryMode());
        assertArrayEquals("{\"readingId\":42,\"metricKey\":\"temperature\"}".getBytes(StandardCharsets.UTF_8),
                sent.getBody());
    }

    @Test
    @DisplayName("ignora los tipos de evento que drenan otros servicios")
    void ignoresEventTypesOwnedByOtherServices() {
        publisher.onOutboxEvent(new SharedEventOutbox("INCIDENT_CREATED", "Incident", "1", "{}", null));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("una caida del broker se propaga para que el outbox reintente")
    void brokerFailuresPropagateSoTheRowStaysPending() {
        doThrow(new IllegalStateException("Connection refused"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        assertThrows(IllegalStateException.class, () -> publisher.onOutboxEvent(readingEvent),
                "the dispatcher must see the failure and keep the row PENDING instead of marking it PUBLISHED");
    }
}
