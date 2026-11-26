package com.test.backend.compliance.messaging;

import com.test.backend.compliance.config.ComplianceRabbitConfiguration;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplianceRabbitConfigurationTest {

    private final ComplianceRabbitConfiguration configuration = new ComplianceRabbitConfiguration();

    @Test
    void securityAuditQueueIsDurableAndDeadLettersToDlq() {
        Queue queue = configuration.securityAuditQueue();

        assertEquals(MqTopology.SECURITY_AUDIT_QUEUE, queue.getName());
        assertTrue(queue.isDurable());
        assertEquals(MqTopology.DEAD_LETTER_EXCHANGE, queue.getArguments().get(MqTopology.DLX_ARGUMENT));
        assertEquals(MqTopology.SECURITY_AUDIT_DLQ, queue.getArguments().get(MqTopology.DLX_ROUTING_KEY_ARGUMENT));
    }

    @Test
    void declaresExchangeBindingAndDeadLetterQueue() {
        TopicExchange events = configuration.safelabEventsExchange();
        DirectExchange deadLetter = configuration.safelabDeadLetterExchange();

        assertEquals(MqTopology.EVENTS_EXCHANGE, events.getName());
        assertEquals(MqTopology.SECURITY_ROUTING_PATTERN, configuration.securityAuditBinding().getRoutingKey());
        assertEquals(MqTopology.DEAD_LETTER_EXCHANGE, deadLetter.getName());
        assertEquals(MqTopology.SECURITY_AUDIT_DLQ, configuration.securityAuditDeadLetterQueue().getName());
        assertEquals(MqTopology.SECURITY_AUDIT_DLQ, configuration.securityAuditDeadLetterBinding().getRoutingKey());
    }
}
