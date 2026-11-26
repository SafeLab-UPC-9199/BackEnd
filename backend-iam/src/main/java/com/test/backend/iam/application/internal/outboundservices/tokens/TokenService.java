package com.test.backend.iam.application.internal.outboundservices.tokens;

import java.util.Collection;

public interface TokenService {
    String generateToken(String email);

    String generateToken(String email, Collection<String> roles, Long workspaceId);

    default String generateToken(String email, Collection<String> roles, Long workspaceId, String sessionId) {
        return generateToken(email, roles, workspaceId);
    }

    String getEmailFromToken(String token);

    default String getSessionIdFromToken(String token) {
        return null;
    }

    boolean validateToken(String token);
}
