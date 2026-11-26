package com.test.backend.iam.application.internal.security;

import com.test.backend.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.test.backend.iam.application.internal.outboundservices.email.EmailService;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.commands.SignInCommand;
import com.test.backend.iam.domain.model.commands.UpdatePasswordCommand;
import com.test.backend.iam.domain.model.entities.Role;
import com.test.backend.iam.domain.model.entities.UserSession;
import com.test.backend.iam.domain.model.exceptions.AccountLockedException;
import com.test.backend.iam.domain.model.valueobjects.Roles;
import com.test.backend.iam.infrastructure.hashing.bcrypt.services.HashingServiceImpl;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.IamRoleRepository;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserSessionRepository;
import com.test.backend.iam.infrastructure.tokens.jwt.services.TokenServiceImpl;
import com.test.backend.iam.interfaces.rest.AuthenticationController;
import com.test.backend.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.test.backend.iam.interfaces.rest.resources.SignInResource;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountProtectionAndSignInTest {

    private UserRepository userRepository;
    private UserSessionRepository userSessionRepository;
    private SharedEventOutboxRepository outboxRepository;
    private TwoFactorService twoFactorService;
    private AccountProtectionService accountProtectionService;
    private TokenServiceImpl tokenService;
    private UserCommandServiceImpl userCommandService;
    private AuthenticationController controller;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userSessionRepository = mock(UserSessionRepository.class);
        outboxRepository = mock(SharedEventOutboxRepository.class);
        var hashingService = new HashingServiceImpl();
        twoFactorService = new TwoFactorService(hashingService);
        accountProtectionService = new AccountProtectionService(userRepository);
        var sessionAuditService = new SessionAuditService(userSessionRepository);
        var securityEventPublisher = new SecurityEventPublisher(outboxRepository);

        tokenService = new TokenServiceImpl();
        ReflectionTestUtils.setField(tokenService, "secret",
                "SafeLabSecretKeyForJwtTokenGeneration2026SecureKey32BytesLong!");
        ReflectionTestUtils.setField(tokenService, "expirationMinutes", 60L);

        userCommandService = new UserCommandServiceImpl(
                userRepository,
                mock(IamRoleRepository.class),
                hashingService,
                tokenService,
                mock(ApplicationEventPublisher.class),
                mock(EmailService.class),
                mock(EntityManager.class),
                twoFactorService,
                sessionAuditService,
                accountProtectionService,
                securityEventPublisher
        );
        controller = new AuthenticationController(userCommandService);

        user = new User("operator@safelab.com", hashingService.encode("Secret123!"), "Operator", List.of(new Role(Roles.ROLE_USER)));
        ReflectionTestUtils.setField(user, "id", 10L);
        user.setConfirmed(true);

        when(userRepository.findByEmail("operator@safelab.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(SharedEventOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void locksAccountOnFiveFailedAttemptsReturns423AndResetsOnSuccess() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");

        for (int i = 0; i < 5; i++) {
            var res = controller.signIn(new SignInResource("operator@safelab.com", "WrongPass"), req);
            assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());
        }

        assertEquals(5, user.getFailedAttempts());
        assertNotNull(user.getLockedUntil());

        var lockedRes = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!"), req);
        assertEquals(HttpStatus.LOCKED, lockedRes.getStatusCode());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) lockedRes.getBody();
        assertNotNull(body.get("lockedUntil"));

        user.setLockedUntil(Instant.now().minusSeconds(10));
        var newIpReq = new MockHttpServletRequest();
        newIpReq.setRemoteAddr("10.0.0.99");
        var okRes = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!"), newIpReq);
        assertEquals(HttpStatus.OK, okRes.getStatusCode());
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockedUntil());

        var ipBlockedRes = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!"), req);
        assertEquals(HttpStatus.LOCKED, ipBlockedRes.getStatusCode());

        ArgumentCaptor<SharedEventOutbox> outboxCaptor = ArgumentCaptor.forClass(SharedEventOutbox.class);
        verify(outboxRepository, times(6)).save(outboxCaptor.capture());
        assertEquals(MqTopology.SECURITY_LOGIN_FAILED_EVENT, outboxCaptor.getAllValues().get(0).getEventType());
        assertEquals(MqTopology.SECURITY_LOGIN_SUCCEEDED_EVENT, outboxCaptor.getAllValues().get(5).getEventType());
    }

    @Test
    void detectsExpiredPasswordAsAtRiskAndClearsAfterPasswordUpdate() {
        user.setPasswordUpdatedAt(Instant.now().minus(95, ChronoUnit.DAYS));

        var res = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!"), new MockHttpServletRequest());
        assertEquals(HttpStatus.OK, res.getStatusCode());
        var auth = (AuthenticatedUserResource) res.getBody();
        assertTrue(auth.atRisk());
        assertTrue(auth.passwordExpired());
        assertEquals("AT_RISK", auth.securityStatus());
        assertNotNull(tokenService.getSessionIdFromToken(auth.token()));

        assertTrue(userCommandService.handle(new UpdatePasswordCommand("operator@safelab.com", "Secret123!", "NewSecret123!")));
        assertFalse(accountProtectionService.isAtRisk(user));
    }

    @Test
    void enforcesTwoFactorChallengeAndValidatesTotpAndEmits2faFailed() {
        String secret = twoFactorService.generateSecret();
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret(secret);

        var challengeRes = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!"), new MockHttpServletRequest());
        assertEquals(HttpStatus.OK, challengeRes.getStatusCode());
        var challenge = (AuthenticatedUserResource) challengeRes.getBody();
        assertTrue(challenge.twoFactorRequired());
        assertNull(challenge.token());
        verify(userSessionRepository, never()).save(any());

        var bad2fa = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!", "000000", null), new MockHttpServletRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, bad2fa.getStatusCode());
        verify(outboxRepository).save(argThat(e -> MqTopology.SECURITY_2FA_FAILED_EVENT.equals(e.getEventType())));

        String validCode = twoFactorService.generateTotp(secret, Instant.now().getEpochSecond() / 30L);
        var okRes = controller.signIn(new SignInResource("operator@safelab.com", "Secret123!", validCode, null), new MockHttpServletRequest());
        assertEquals(HttpStatus.OK, okRes.getStatusCode());
        var okBody = (AuthenticatedUserResource) okRes.getBody();
        assertFalse(okBody.twoFactorRequired());
        assertNotNull(okBody.token());
        verify(userSessionRepository, times(1)).save(any());
    }
}
