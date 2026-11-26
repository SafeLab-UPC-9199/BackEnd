package com.test.backend.compliance.application.internal.eventhandlers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.entities.ProcessedSecurityEvent;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.ProcessedSecurityEventRepository;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class SecurityAuditConsumer {

    private static final String SECURITY_TARGET_TYPE = "SECURITY";

    private final ProcessedSecurityEventRepository processedEventRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public SecurityAuditConsumer(ProcessedSecurityEventRepository processedEventRepository,
                                 AuditTrailRepository auditTrailRepository) {
        this.processedEventRepository = processedEventRepository;
        this.auditTrailRepository = auditTrailRepository;
    }

    @RabbitListener(queues = MqTopology.SECURITY_AUDIT_QUEUE)
    @Transactional
    public void onMessage(Message message) {
        var event = deserialize(message);
        var eventId = firstNonBlank(event.eventId(), message.getMessageProperties().getMessageId(), "unkeyed-" + UUID.randomUUID());
        if (processedEventRepository.existsByEventId(eventId)) {
            return;
        }

        var eventType = firstNonBlank(event.eventType(), message.getMessageProperties().getReceivedRoutingKey(), "security.event");
        processedEventRepository.saveAndFlush(new ProcessedSecurityEvent(
                eventId,
                truncate(eventType, 100),
                truncate(event.actor(), 64),
                Instant.now()
        ));

        auditTrailRepository.save(new AuditTrail(
                event.actor(),
                SECURITY_TARGET_TYPE,
                eventType,
                event.actor(),
                event.organizationId(),
                event.ipAddress(),
                truncate(event.details(), 1000)
        ));
    }

    private SecurityAuditPayload deserialize(Message message) {
        var body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(body, SecurityAuditPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Malformed security event payload: " + ex.getMessage(), ex);
        }
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecurityAuditPayload(
            String eventId,
            String eventType,
            String actor,
            Long organizationId,
            String ipAddress,
            String details,
            Instant timestamp
    ) {}
}
