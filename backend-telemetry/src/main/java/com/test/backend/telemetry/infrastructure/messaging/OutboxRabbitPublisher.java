package com.test.backend.telemetry.infrastructure.messaging;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Bridge between the transactional outbox (ADR-002) and RabbitMQ (ADR-005):
 * forwards the outbox rows drained by {@code SharedEventOutboxDispatcher} to the
 * topic exchange, so telemetry never calls alerts directly and no reading can be
 * lost between the database commit and the broker.
 */
@Component
public class OutboxRabbitPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRabbitPublisher.class);

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final RabbitTemplate rabbitTemplate;

    public OutboxRabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * The dispatcher publishes outbox rows through {@code ApplicationEventPublisher}
     * synchronously, inside its own transaction. A broker outage must therefore
     * propagate as an exception: the dispatcher keeps the row PENDING and retries
     * it on the next scheduled run instead of marking it PUBLISHED. Delivery is
     * at-least-once, and alerts discards duplicates through its consumer inbox.
     *
     * <p>Rows of other event types are ignored here (they belong to other owners;
     * see {@code outbox.dispatch-event-types}).
     */
    @EventListener
    public void onOutboxEvent(SharedEventOutbox outbox) {
        if (!MqTopology.TEMPERATURE_READING_EVENT_TYPE.equals(outbox.getEventType())) {
            return;
        }

        var properties = new MessageProperties();
        properties.setContentType(CONTENT_TYPE_JSON);
        // AMQP messageId = outbox eventId: the consumer uses it as idempotency key.
        properties.setMessageId(outbox.getEventId());
        properties.setCorrelationId(outbox.getCorrelationId());
        // Persistent delivery mode + durable queue: messages survive a broker restart.
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        var message = new Message(outbox.getPayload().getBytes(StandardCharsets.UTF_8), properties);
        rabbitTemplate.send(MqTopology.EVENTS_EXCHANGE, MqTopology.TEMPERATURE_READING_ROUTING_KEY, message);
        logger.info("Published outbox event {} ({}) to exchange '{}' with routing key '{}'",
                outbox.getEventId(), outbox.getEventType(),
                MqTopology.EVENTS_EXCHANGE, MqTopology.TEMPERATURE_READING_ROUTING_KEY);
    }
}
