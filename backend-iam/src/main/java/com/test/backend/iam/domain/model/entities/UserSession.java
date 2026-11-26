package com.test.backend.iam.domain.model.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_sessions", catalog = "safelab_iam")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String email;

    private Long organizationId;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Column(length = 120)
    private String location;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public UserSession(String sessionId, Long userId, String email, Long organizationId,
                       String ipAddress, String userAgent, String location,
                       Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.email = email;
        this.organizationId = organizationId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.location = location;
        this.createdAt = createdAt;
        this.lastActivityAt = createdAt;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public boolean isActive() {
        return !revoked && expiresAt != null && expiresAt.isAfter(Instant.now());
    }
}
