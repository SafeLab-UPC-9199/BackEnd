package com.test.backend.iam.interfaces.rest;

import com.test.backend.iam.application.internal.outboundservices.hashing.HashingService;
import com.test.backend.iam.application.internal.security.AccountProtectionService;
import com.test.backend.iam.application.internal.security.SecurityEventPublisher;
import com.test.backend.iam.application.internal.security.SessionAuditService;
import com.test.backend.iam.application.internal.security.TwoFactorService;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.test.backend.iam.infrastructure.tokens.jwt.BearerTokenService;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(value = "/api/v1/security", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Security", description = "2FA, Session Audit & Account Protection Endpoints")
public class SecurityController {

    private final UserRepository userRepository;
    private final HashingService hashingService;
    private final TwoFactorService twoFactorService;
    private final SessionAuditService sessionAuditService;
    private final AccountProtectionService accountProtectionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final BearerTokenService tokenService;

    public SecurityController(UserRepository userRepository,
                              HashingService hashingService,
                              TwoFactorService twoFactorService,
                              SessionAuditService sessionAuditService,
                              AccountProtectionService accountProtectionService,
                              SecurityEventPublisher securityEventPublisher,
                              BearerTokenService tokenService) {
        this.userRepository = userRepository;
        this.hashingService = hashingService;
        this.twoFactorService = twoFactorService;
        this.sessionAuditService = sessionAuditService;
        this.accountProtectionService = accountProtectionService;
        this.securityEventPublisher = securityEventPublisher;
        this.tokenService = tokenService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        boolean passwordExpired = accountProtectionService.isAtRisk(user);
        int activeSessionsCount = sessionAuditService.listActiveSessions(user.getId()).size();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("twoFactorEnabled", user.isTwoFactorEnabled());
        body.put("atRisk", passwordExpired);
        body.put("passwordExpired", passwordExpired);
        body.put("securityStatus", passwordExpired ? "AT_RISK" : "SECURE");
        body.put("failedAttempts", user.getFailedAttempts());
        body.put("lockedUntil", user.getLockedUntil());
        body.put("activeSessionsCount", activeSessionsCount);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setupTwoFactor() {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        String secret = twoFactorService.generateSecret();
        user.setTwoFactorSecret(secret);
        userRepository.save(user);
        String uri = twoFactorService.buildOtpAuthUri(user.getEmail(), secret);
        return ResponseEntity.ok(Map.of("secret", secret, "otpauthUri", uri));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enableTwoFactor(@RequestBody CodeRequest request) {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        if (user.getTwoFactorSecret() == null || request == null
                || !twoFactorService.verifyTotp(user.getTwoFactorSecret(), request.code())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid verification code"));
        }
        List<String> backupCodes = twoFactorService.generateBackupCodes();
        user.setTwoFactorEnabled(true);
        user.setBackupCodesHash(twoFactorService.hashBackupCodes(backupCodes));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("enabled", true, "backupCodes", backupCodes));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disableTwoFactor(@RequestBody DisableTwoFactorRequest request) {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        boolean hasPassword = request != null && request.password() != null && !request.password().isBlank();
        boolean hasCode = request != null && request.code() != null && !request.code().isBlank();
        if (!hasPassword && !hasCode) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Password or verification code is required"));
        }
        if (hasPassword && !hashingService.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid password"));
        }
        if (hasCode && user.isTwoFactorEnabled()) {
            boolean validTotp = twoFactorService.verifyTotp(user.getTwoFactorSecret(), request.code());
            boolean validBackup = !validTotp && twoFactorService.verifyAndConsumeBackupCode(user, request.code());
            if (!validTotp && !validBackup) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Invalid verification code"));
            }
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setBackupCodesHash(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("enabled", false));
    }

    @PostMapping("/2fa/backup-codes")
    public ResponseEntity<?> regenerateBackupCodes() {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        if (!user.isTwoFactorEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Two-factor authentication is not enabled"));
        }
        List<String> backupCodes = twoFactorService.generateBackupCodes();
        user.setBackupCodesHash(twoFactorService.hashBackupCodes(backupCodes));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("backupCodes", backupCodes));
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(HttpServletRequest request) {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String currentSessionId = resolveCurrentSessionId(request);
        var sessions = sessionAuditService.listActiveSessions(userOpt.get().getId()).stream()
                .map(s -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sessionId", s.getSessionId());
                    item.put("ipAddress", s.getIpAddress());
                    item.put("userAgent", s.getUserAgent());
                    item.put("location", s.getLocation());
                    item.put("createdAt", s.getCreatedAt());
                    item.put("lastActivityAt", s.getLastActivityAt());
                    item.put("current", s.getSessionId().equals(currentSessionId));
                    return item;
                })
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId, HttpServletRequest request) {
        var userOpt = resolveCurrentUser();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var user = userOpt.get();
        var revokedOpt = sessionAuditService.revokeSession(sessionId, user.getId());
        if (revokedOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var session = revokedOpt.get();
        Long organizationId = RequestIdentity.currentWorkspaceId().orElse(session.getOrganizationId());
        securityEventPublisher.publishSessionRevoked(
                user.getEmail(),
                organizationId,
                extractClientIp(request),
                "Session revoked: " + sessionId
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{sessionId}/active")
    public ResponseEntity<Map<String, Boolean>> isSessionActive(@PathVariable String sessionId) {
        return ResponseEntity.ok(Map.of("active", sessionAuditService.isSessionActive(sessionId)));
    }

    private Optional<User> resolveCurrentUser() {
        var emailOpt = RequestIdentity.currentUserEmail();
        if (emailOpt.isEmpty()) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                emailOpt = Optional.of(auth.getName());
            }
        }
        return emailOpt.flatMap(userRepository::findByEmail);
    }

    private String resolveCurrentSessionId(HttpServletRequest request) {
        String fromHeader = RequestIdentity.sessionId();
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        if (request == null) {
            return null;
        }
        String token = tokenService.getBearerTokenFrom(request);
        return token != null ? tokenService.getSessionIdFromToken(token) : null;
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    public record CodeRequest(String code) {
    }

    public record DisableTwoFactorRequest(String password, String code) {
    }
}
