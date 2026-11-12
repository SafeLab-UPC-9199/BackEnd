package com.test.backend.alerts.messaging;

import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.alerts.config.RabbitTopologyConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer side of the ADR-005 topology: identical to the producer's
 * declaration, so startup order does not matter and either side can rebuild the
 * topology after a broker wipe.
 */
class RabbitTopologyConfigurationTest {

    private final RabbitTopologyConfiguration configuration = new RabbitTopologyConfiguration();

    @Test
    @DisplayName("la cola consumida es durable y dead-lettera a la DLQ")
    void readingQueueIsDurableAndDeadLettersToTheDlq() {
        Queue queue = configuration.temperatureReadingQueue();

        assertEquals(MqTopology.TEMPERATURE_READING_QUEUE, queue.getName());
        assertTrue(queue.isDurable());
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
        assertEquals(MqTopology.TEMPERATURE_READING_ROUTING_KEY,
                configuration.temperatureReadingBinding().getRoutingKey());
        assertEquals(MqTopology.DEAD_LETTER_EXCHANGE, deadLetter.getName());
        assertEquals(MqTopology.TEMPERATURE_READING_DLQ,
                configuration.temperatureReadingDeadLetterQueue().getName());
        assertEquals(MqTopology.TEMPERATURE_READING_DLQ,
                configuration.temperatureReadingDeadLetterBinding().getRoutingKey());
    }
}
