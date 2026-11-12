package com.test.backend.telemetry.config;

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
 * Declares the full AMQP topology of the telemetry -> alerts flow (ADR-005):
 * topic exchange, consumer queue with dead-letter arguments, the dead-letter
 * exchange and its queue.
 *
 * <p>The producer declares everything (not only the exchange) so a message can
 * never be published to a not-yet-bound exchange and silently dropped while
 * alerts is still booting: readings just buffer in the durable queue until the
 * consumer attaches. Declarations are idempotent, and backend-alerts declares
 * the very same objects from {@code MqTopology}, so starting order does not
 * matter and either side can rebuild the topology after a broker wipe.
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
