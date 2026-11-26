package com.test.backend.iam.infrastructure.messaging;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SecurityOutboxRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(SecurityOutboxRabbitBridge.class);

    private final RabbitTemplate rabbitTemplate;

    public SecurityOutboxRabbitBridge(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Bean
    public TopicExchange safelabEventsExchange() {
        return new TopicExchange(MqTopology.EVENTS_EXCHANGE, true, false);
    }

    @EventListener
    public void onOutboxEvent(SharedEventOutbox outbox) {
        if (outbox.getEventType() == null || !outbox.getEventType().startsWith("security.")) {
            return;
        }
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(outbox.getEventId());
        properties.setCorrelationId(outbox.getCorrelationId());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

        var message = new Message(outbox.getPayload().getBytes(StandardCharsets.UTF_8), properties);
        rabbitTemplate.send(MqTopology.EVENTS_EXCHANGE, outbox.getEventType(), message);
        logger.debug("Published security outbox event {} ({})", outbox.getEventId(), outbox.getEventType());
    }
}
