package com.test.backend.telemetry.messaging;

import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.telemetry.config.RabbitTopologyConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer declares the whole topology (ADR-005) so readings published while
 * alerts is still booting are buffered in the durable queue instead of being
 * dropped by an exchange with no bound queue.
 */
class RabbitTopologyConfigurationTest {

    private final RabbitTopologyConfiguration configuration = new RabbitTopologyConfiguration();

    @Test
    @DisplayName("la cola de lecturas es durable y dead-lettera a la DLQ")
    void readingQueueIsDurableAndDeadLettersToTheDlq() {
        Queue queue = configuration.temperatureReadingQueue();

        assertEquals(MqTopology.TEMPERATURE_READING_QUEUE, queue.getName());
        assertTrue(queue.isDurable(), "readings must survive a broker restart");
        assertEquals(MqTopology.DEAD_LETTER_EXCHANGE,
                queue.getArguments().get(MqTopology.DLX_ARGUMENT));
        assertEquals(MqTopology.TEMPERATURE_READING_DLQ,
                queue.getArguments().get(MqTopology.DLX_ROUTING_KEY_ARGUMENT));
    }

    @Test
    @DisplayName("declara el exchange de eventos, el binding de lecturas y la DLQ")
    void declaresExchangeBindingAndDeadLetterQueue() {
        TopicExchange events = configuration.safelabEventsExchange();
        DirectExchange deadLetter = configuration.safelabDeadLetterExchange();

        assertEquals(MqTopology.EVENTS_EXCHANGE, events.getName());
        assertTrue(events.isDurable());
        assertEquals(MqTopology.TEMPERATURE_READING_ROUTING_KEY,
                configuration.temperatureReadingBinding().getRoutingKey());
        // Binding destination is the queue; the exchange it hangs from is getExchange().
        assertEquals(MqTopology.TEMPERATURE_READING_QUEUE,
                configuration.temperatureReadingBinding().getDestination());
        assertEquals(MqTopology.EVENTS_EXCHANGE,
                configuration.temperatureReadingBinding().getExchange());

        assertEquals(MqTopology.DEAD_LETTER_EXCHANGE, deadLetter.getName());
        assertEquals(MqTopology.TEMPERATURE_READING_DLQ,
                configuration.temperatureReadingDeadLetterQueue().getName());
        assertEquals(MqTopology.TEMPERATURE_READING_DLQ,
                configuration.temperatureReadingDeadLetterBinding().getRoutingKey());
        assertNotNull(configuration.temperatureReadingDeadLetterBinding());
    }
}
