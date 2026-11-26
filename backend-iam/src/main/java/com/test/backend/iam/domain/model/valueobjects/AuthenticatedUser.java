package com.test.backend.iam.domain.model.valueobjects;

import com.test.backend.iam.domain.model.aggregates.User;

public record AuthenticatedUser(
        User user,
        String token,
        Long workspaceId,
        boolean twoFactorRequired,
        boolean atRisk,
        boolean passwordExpired,
        String securityStatus
) {
    public AuthenticatedUser(User user, String token) {
        this(user, token, null, false, false, false, "SECURE");
    }
}
