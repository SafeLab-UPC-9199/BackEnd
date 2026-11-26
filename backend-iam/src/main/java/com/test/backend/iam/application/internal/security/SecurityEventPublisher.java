package com.test.backend.iam.application.internal.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityEventPublisher {

    private final SharedEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SecurityEventPublisher(SharedEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publishLoginSucceeded(String actor, Long organizationId, String ipAddress, String details) {
        publish(MqTopology.SECURITY_LOGIN_SUCCEEDED_EVENT, actor, organizationId, ipAddress, details);
    }

    public void publishLoginFailed(String actor, Long organizationId, String ipAddress, String details) {
        publish(MqTopology.SECURITY_LOGIN_FAILED_EVENT, actor, organizationId, ipAddress, details);
    }

    public void publish2faFailed(String actor, Long organizationId, String ipAddress, String details) {
        publish(MqTopology.SECURITY_2FA_FAILED_EVENT, actor, organizationId, ipAddress, details);
    }

    public void publishSessionRevoked(String actor, Long organizationId, String ipAddress, String details) {
        publish(MqTopology.SECURITY_SESSION_REVOKED_EVENT, actor, organizationId, ipAddress, details);
    }

    public void publish(String eventType, String actor, Long organizationId, String ipAddress, String details) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType);
        body.put("actor", actor);
        body.put("organizationId", organizationId);
        body.put("ipAddress", ipAddress);
        body.put("details", details);
        body.put("timestamp", Instant.now().toString());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize security event payload", e);
        }

        var outbox = new SharedEventOutbox(
                eventType,
                "User",
                actor != null ? actor : "unknown",
                payload,
                organizationId != null ? organizationId.toString() : null
        );
        outbox.setEventId(eventId);
        outboxRepository.save(outbox);
    }
}
