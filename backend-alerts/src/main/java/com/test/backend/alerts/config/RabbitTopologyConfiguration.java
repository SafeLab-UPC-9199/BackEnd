package com.test.backend.alerts.config;

import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the consumer side of the telemetry -> alerts flow (ADR-005): the
 * topic exchange, the queue consumed by alerts, the dead-letter arguments that
 * park unprocessable messages, the dead-letter exchange and its queue.
 *
 * <p>Identical to the producer's declaration (both are built from the constants
 * in {@link MqTopology}); re-declaring is idempotent in RabbitMQ, which makes
 * the startup order of telemetry and alerts irrelevant and lets alerts rebuild
 * everything after a broker wipe.
 */
@Configuration
public class RabbitTopologyConfiguration {

    @Bean
    public TopicExchange safelabEventsExchange() {
        return new TopicExchange(MqTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange safelabDeadLetterExchange() {
        return new DirectExchange(MqTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue temperatureReadingQueue() {
        return QueueBuilder.durable(MqTopology.TEMPERATURE_READING_QUEUE)
                .withArgument(MqTopology.DLX_ARGUMENT, MqTopology.DEAD_LETTER_EXCHANGE)
                .withArgument(MqTopology.DLX_ROUTING_KEY_ARGUMENT, MqTopology.TEMPERATURE_READING_DLQ)
                .build();
    }

    @Bean
    public Binding temperatureReadingBinding() {
        return BindingBuilder.bind(temperatureReadingQueue())
                .to(safelabEventsExchange())
                .with(MqTopology.TEMPERATURE_READING_ROUTING_KEY);
    }

    @Bean
    public Queue temperatureReadingDeadLetterQueue() {
        return QueueBuilder.durable(MqTopology.TEMPERATURE_READING_DLQ).build();
    }

    @Bean
    public Binding temperatureReadingDeadLetterBinding() {
        return BindingBuilder.bind(temperatureReadingDeadLetterQueue())
                .to(safelabDeadLetterExchange())
                .with(MqTopology.TEMPERATURE_READING_DLQ);
    }
}
