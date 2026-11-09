package com.test.backend.iam.application.internal.outboundservices.tokens;

import java.util.Collection;

public interface TokenService {
    String generateToken(String email);

    /**
     * Signs a token carrying the identity claims consumed by the edge gateway
     * (Fase 6, ADR-006): {@code roles} (ROLE_* names) and {@code workspaceId}
     * (nullable: omitted when the caller has no workspace yet).
     */
    String generateToken(String email, Collection<String> roles, Long workspaceId);

    String getEmailFromToken(String token);

    boolean validateToken(String token);
}
