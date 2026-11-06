package com.test.backend.alerts.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JWTSecurityContextAdapter {

    public String getCurrentActor() {
        return "system-automation";
    }

    public Optional<Long> getCurrentWorkspaceId() {
        return Optional.empty();
    }

    public boolean isCurrentUserAdmin() {
        return true;
    }
}
