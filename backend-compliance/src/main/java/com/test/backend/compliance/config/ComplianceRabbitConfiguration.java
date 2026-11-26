package com.test.backend.compliance.config;

import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComplianceRabbitConfiguration {

    @Bean
    public TopicExchange safelabEventsExchange() {
        return new TopicExchange(MqTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange safelabDeadLetterExchange() {
        return new DirectExchange(MqTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue securityAuditQueue() {
        return QueueBuilder.durable(MqTopology.SECURITY_AUDIT_QUEUE)
                .withArgument(MqTopology.DLX_ARGUMENT, MqTopology.DEAD_LETTER_EXCHANGE)
                .withArgument(MqTopology.DLX_ROUTING_KEY_ARGUMENT, MqTopology.SECURITY_AUDIT_DLQ)
                .build();
    }

    @Bean
    public Binding securityAuditBinding() {
        return BindingBuilder.bind(securityAuditQueue())
                .to(safelabEventsExchange())
                .with(MqTopology.SECURITY_ROUTING_PATTERN);
    }

    @Bean
    public Queue securityAuditDeadLetterQueue() {
        return QueueBuilder.durable(MqTopology.SECURITY_AUDIT_DLQ).build();
    }

    @Bean
    public Binding securityAuditDeadLetterBinding() {
        return BindingBuilder.bind(securityAuditDeadLetterQueue())
                .to(safelabDeadLetterExchange())
                .with(MqTopology.SECURITY_AUDIT_DLQ);
    }
}
