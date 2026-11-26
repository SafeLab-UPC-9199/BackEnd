package com.test.backend.compliance.messaging;

import com.test.backend.compliance.application.internal.eventhandlers.SecurityAuditConsumer;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.entities.ProcessedSecurityEvent;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.ProcessedSecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityAuditConsumerTest {

    private static final String LOGIN_EVENT = """
            {"eventId":"evt-1","eventType":"security.login.succeeded","actor":"alice@safelab.io",
             "organizationId":7,"ipAddress":"10.0.0.5","details":"2FA verified","timestamp":"2026-11-26T10:00:00Z"}""";

    private ProcessedSecurityEventRepository processedEventRepository;
    private AuditTrailRepository auditTrailRepository;
    private SecurityAuditConsumer consumer;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedSecurityEventRepository.class);
        auditTrailRepository = mock(AuditTrailRepository.class);
        consumer = new SecurityAuditConsumer(processedEventRepository, auditTrailRepository);
    }

    @Test
    void persistsSecurityEventIdempotently() {
        consumer.onMessage(message(null, null, LOGIN_EVENT));

        ArgumentCaptor<ProcessedSecurityEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedSecurityEvent.class);
        verify(processedEventRepository).saveAndFlush(processedCaptor.capture());
        assertEquals("evt-1", processedCaptor.getValue().getEventId());
        assertEquals("security.login.succeeded", processedCaptor.getValue().getEventType());
        assertEquals("alice@safelab.io", processedCaptor.getValue().getAggregateId());

        ArgumentCaptor<AuditTrail> trailCaptor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(trailCaptor.capture());
        AuditTrail saved = trailCaptor.getValue();
        assertEquals("SECURITY", saved.getTargetType());
        assertEquals("alice@safelab.io", saved.getTargetId());
        assertEquals("security.login.succeeded", saved.getAction());
        assertEquals("alice@safelab.io", saved.getActor());
        assertEquals(7L, saved.getOrganizationId());
        assertEquals("10.0.0.5", saved.getIpAddress());
        assertEquals("2FA verified", saved.getDetails());
    }

    @Test
    void skipsDuplicateEvent() {
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(true);

        consumer.onMessage(message(null, null, LOGIN_EVENT));

        verify(processedEventRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditTrailRepository);
    }

    @Test
    void fallsBackToAmqpMessageIdAndRoutingKey() {
        String body = """
                {"actor":"bob@safelab.io","organizationId":3,"ipAddress":"192.168.1.10","details":"Bad TOTP"}""";

        consumer.onMessage(message("amqp-msg-99", "security.2fa.failed", body));

        verify(processedEventRepository).existsByEventId("amqp-msg-99");
        ArgumentCaptor<AuditTrail> trailCaptor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository).save(trailCaptor.capture());
        assertEquals("security.2fa.failed", trailCaptor.getValue().getAction());
        assertEquals("SECURITY", trailCaptor.getValue().getTargetType());
    }

    @Test
    void rejectsMalformedPayloadWithoutPersisting() {
        assertThrows(IllegalStateException.class,
                () -> consumer.onMessage(message("amqp-msg-bad", "security.login.failed", "{bad-json")));

        verifyNoInteractions(processedEventRepository, auditTrailRepository);
    }

    private Message message(String messageId, String routingKey, String body) {
        var props = new MessageProperties();
        props.setMessageId(messageId);
        props.setReceivedRoutingKey(routingKey);
        props.setContentType("application/json");
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }
}
