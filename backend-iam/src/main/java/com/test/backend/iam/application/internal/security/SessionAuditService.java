package com.test.backend.iam.application.internal.security;

import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.entities.UserSession;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserSessionRepository;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionAuditService {

    private final UserSessionRepository userSessionRepository;

    @Value("${authorization.jwt.expiration.minutes:60}")
    private long expirationMinutes = 60;

    public SessionAuditService(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    public UserSession createSession(User user, Long organizationId, String ipAddress, String userAgent, String location) {
        Instant now = Instant.now();
        long ttlMinutes = expirationMinutes > 0 ? expirationMinutes : 60;
        String normalizedAgent = normalizeText(userAgent, "Unknown", 255);
        String normalizedLocation = normalizeText(location, "Unknown", 120);
        String normalizedIp = normalizeText(ipAddress, "unknown", 64);

        var session = new UserSession(
                UUID.randomUUID().toString(),
                user.getId(),
                user.getEmail(),
                organizationId,
                normalizedIp,
                normalizedAgent,
                normalizedLocation,
                now,
                now.plusSeconds(ttlMinutes * 60)
        );
        return userSessionRepository.save(session);
    }

    public List<UserSession> listActiveSessions(Long userId) {
        return userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastActivityAtDesc(
                userId, Instant.now());
    }

    public Optional<UserSession> revokeSession(String sessionId, Long actorUserId) {
        var sessionOpt = userSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        var session = sessionOpt.get();
        if (!session.getUserId().equals(actorUserId) && !RequestIdentity.isAdmin()) {
            return Optional.empty();
        }
        if (!session.isRevoked()) {
            session.setRevoked(true);
            session.setRevokedAt(Instant.now());
            userSessionRepository.save(session);
        }
        return Optional.of(session);
    }

    public boolean isSessionActive(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return userSessionRepository.findBySessionId(sessionId)
                .map(UserSession::isActive)
                .orElse(false);
    }

    private String normalizeText(String value, String fallback, int maxLen) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }
}
