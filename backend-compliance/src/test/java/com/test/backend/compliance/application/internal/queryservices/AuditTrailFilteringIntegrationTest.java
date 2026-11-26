package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.compliance.application.internal.eventhandlers.SecurityAuditConsumer;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.ProcessedSecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:compliance_test;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS safelab_compliance",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AuditTrailFilteringIntegrationTest {

    @Autowired
    private SecurityAuditConsumer consumer;

    @Autowired
    private AuditTrailQueryService queryService;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private ProcessedSecurityEventRepository processedEventRepository;

    @BeforeEach
    void clean() {
        auditTrailRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void filtersByUserOrganizationIdAndDateRangeAndDeduplicatesConsumer() throws Exception {
        consumer.onMessage(jsonMessage("e-1", "security.login.succeeded", "alice@org1.com", 1L, "1.1.1.1"));
        consumer.onMessage(jsonMessage("e-1", "security.login.succeeded", "alice@org1.com", 1L, "1.1.1.1"));

        Thread.sleep(20);
        Instant midpoint = Instant.now();
        Thread.sleep(20);

        consumer.onMessage(jsonMessage("e-2", "security.2fa.failed", "bob@org2.com", 2L, "2.2.2.2"));

        assertEquals(2, auditTrailRepository.count());

        var byUser = queryService.handle(new GetAuditTrailsQuery(null, null, "alice@org1.com", null, null, null));
        assertEquals(1, byUser.size());
        assertEquals("1.1.1.1", byUser.get(0).getIpAddress());

        var byOrg = queryService.handle(new GetAuditTrailsQuery(null, null, null, 2L, null, null));
        assertEquals(1, byOrg.size());
        assertEquals("bob@org2.com", byOrg.get(0).getActor());

        var afterMidpoint = queryService.handle(new GetAuditTrailsQuery(null, null, null, null, midpoint, null));
        assertEquals(1, afterMidpoint.size());
        assertEquals("bob@org2.com", afterMidpoint.get(0).getActor());

        var beforeMidpoint = queryService.handle(new GetAuditTrailsQuery(null, null, null, null, null, midpoint));
        assertEquals(1, beforeMidpoint.size());
        assertEquals("alice@org1.com", beforeMidpoint.get(0).getActor());
    }

    private Message jsonMessage(String eventId, String eventType, String actor, Long orgId, String ip) {
        String json = String.format(
                "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"actor\":\"%s\",\"organizationId\":%d,\"ipAddress\":\"%s\",\"details\":\"test\"}",
                eventId, eventType, actor, orgId, ip
        );
        var props = new MessageProperties();
        props.setMessageId(eventId);
        props.setContentType("application/json");
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }
}
