package com.test.backend.iam.interfaces.rest.resources;

import java.util.List;

public record AuthenticatedUserResource(
        Long id,
        String username,
        String token,
        List<String> roles,
        Long workspaceId,
        boolean twoFactorRequired,
        boolean atRisk,
        boolean passwordExpired,
        String securityStatus
) {
    public AuthenticatedUserResource(Long id, String username, String token, List<String> roles) {
        this(id, username, token, roles, null, false, false, false, "SECURE");
    }
}
