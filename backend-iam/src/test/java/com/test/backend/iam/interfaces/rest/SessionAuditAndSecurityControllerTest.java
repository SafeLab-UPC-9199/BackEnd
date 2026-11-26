package com.test.backend.iam.interfaces.rest;

import com.test.backend.iam.application.internal.security.AccountProtectionService;
import com.test.backend.iam.application.internal.security.SecurityEventPublisher;
import com.test.backend.iam.application.internal.security.SessionAuditService;
import com.test.backend.iam.application.internal.security.TwoFactorService;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.entities.UserSession;
import com.test.backend.iam.infrastructure.hashing.bcrypt.services.HashingServiceImpl;
import com.test.backend.iam.infrastructure.messaging.SecurityOutboxRabbitBridge;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserSessionRepository;
import com.test.backend.iam.infrastructure.tokens.jwt.services.TokenServiceImpl;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionAuditAndSecurityControllerTest {

    private UserRepository userRepository;
    private UserSessionRepository sessionRepository;
    private SharedEventOutboxRepository outboxRepository;
    private TwoFactorService twoFactorService;
    private SessionAuditService sessionAuditService;
    private SecurityController controller;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        sessionRepository = mock(UserSessionRepository.class);
        outboxRepository = mock(SharedEventOutboxRepository.class);
        var hashingService = new HashingServiceImpl();
        twoFactorService = new TwoFactorService(hashingService);
        sessionAuditService = new SessionAuditService(sessionRepository);
        var accountProtection = new AccountProtectionService(userRepository);
        var eventPublisher = new SecurityEventPublisher(outboxRepository);

        controller = new SecurityController(
                userRepository,
                hashingService,
                twoFactorService,
                sessionAuditService,
                accountProtection,
                eventPublisher,
                new TokenServiceImpl()
        );

        user = new User("sec@safelab.com", hashingService.encode("Pass123!"), "Sec User");
        ReflectionTestUtils.setField(user, "id", 42L);
        when(userRepository.findByEmail("sec@safelab.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "sec@safelab.com");
        req.addHeader("X-Session-Id", "sess-1");
        req.addHeader("X-Workspace-Id", "3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void enablesAndDisablesTwoFactorViaEndpoints() {
        var setupRes = controller.setupTwoFactor();
        assertEquals(HttpStatus.OK, setupRes.getStatusCode());
        @SuppressWarnings("unchecked")
        var setupBody = (Map<String, Object>) setupRes.getBody();
        String secret = (String) setupBody.get("secret");
        assertTrue(((String) setupBody.get("otpauthUri")).contains("SafeLab:sec@safelab.com"));

        String code = twoFactorService.generateTotp(secret, Instant.now().getEpochSecond() / 30L);
        var enableRes = controller.enableTwoFactor(new SecurityController.CodeRequest(code));
        assertEquals(HttpStatus.OK, enableRes.getStatusCode());
        @SuppressWarnings("unchecked")
        var enableBody = (Map<String, Object>) enableRes.getBody();
        assertEquals(true, enableBody.get("enabled"));
        assertEquals(8, ((List<?>) enableBody.get("backupCodes")).size());
        assertTrue(user.isTwoFactorEnabled());

        var badPasswordRes = controller.disableTwoFactor(new SecurityController.DisableTwoFactorRequest("WrongPass", null));
        assertEquals(HttpStatus.BAD_REQUEST, badPasswordRes.getStatusCode());
        var missingBothRes = controller.disableTwoFactor(new SecurityController.DisableTwoFactorRequest(null, null));
        assertEquals(HttpStatus.BAD_REQUEST, missingBothRes.getStatusCode());

        var disableWithCodeRes = controller.disableTwoFactor(new SecurityController.DisableTwoFactorRequest(null, code));
        assertEquals(HttpStatus.OK, disableWithCodeRes.getStatusCode());
        assertFalse(user.isTwoFactorEnabled());

        user.setTwoFactorSecret(secret);
        controller.enableTwoFactor(new SecurityController.CodeRequest(
                twoFactorService.generateTotp(secret, Instant.now().getEpochSecond() / 30L)));
        var disableWithPasswordRes = controller.disableTwoFactor(new SecurityController.DisableTwoFactorRequest("Pass123!", null));
        assertEquals(HttpStatus.OK, disableWithPasswordRes.getStatusCode());
        assertFalse(user.isTwoFactorEnabled());
    }

    @Test
    void listsAndRevokesActiveSessionsAndEmitsOutboxEvent() {
        var session = new UserSession(
                "sess-1", 42L, "sec@safelab.com", 3L,
                "192.168.1.10", "Mozilla/5.0", "Lima",
                Instant.now(), Instant.now().plusSeconds(3600)
        );
        when(sessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastActivityAtDesc(eq(42L), any()))
                .thenReturn(List.of(session));
        when(sessionRepository.findBySessionId("sess-1")).thenReturn(Optional.of(session));

        assertEquals(Boolean.TRUE, controller.isSessionActive("sess-1").getBody().get("active"));

        var listRes = controller.listSessions(new MockHttpServletRequest());
        assertEquals(HttpStatus.OK, listRes.getStatusCode());
        @SuppressWarnings("unchecked")
        var list = (List<Map<String, Object>>) listRes.getBody();
        assertEquals(1, list.size());
        assertEquals(true, list.get(0).get("current"));

        var delRes = controller.revokeSession("sess-1", new MockHttpServletRequest());
        assertEquals(HttpStatus.NO_CONTENT, delRes.getStatusCode());
        assertTrue(session.isRevoked());
        assertNotNull(session.getRevokedAt());
        assertEquals(Boolean.FALSE, controller.isSessionActive("sess-1").getBody().get("active"));

        verify(outboxRepository).save(argThat(e -> MqTopology.SECURITY_SESSION_REVOKED_EVENT.equals(e.getEventType())));
    }

    @Test
    void securityOutboxBridgePublishesSecurityEventsAndIgnoresOthers() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        var bridge = new SecurityOutboxRabbitBridge(rabbitTemplate);

        var secEvent = new SharedEventOutbox(
                MqTopology.SECURITY_LOGIN_SUCCEEDED_EVENT, "User", "sec@safelab.com", "{\"actor\":\"sec@safelab.com\"}", "3");
        bridge.onOutboxEvent(secEvent);

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(MqTopology.EVENTS_EXCHANGE),
                eq(MqTopology.SECURITY_LOGIN_SUCCEEDED_EVENT),
                msgCaptor.capture());
        assertEquals(secEvent.getEventId(), msgCaptor.getValue().getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT, msgCaptor.getValue().getMessageProperties().getDeliveryMode());

        bridge.onOutboxEvent(new SharedEventOutbox("TemperatureReadingRecorded", "SensorReading", "1", "{}", null));
        verifyNoMoreInteractions(rabbitTemplate);
    }
}
