package com.test.backend.alerts.infrastructure.security;

import com.test.backend.shared.infrastructure.security.RequestIdentity;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Exposes the caller's identity to the incident services.
 *
 * <p>Since Fase 6 (ADR-006) the edge gateway validates the JWT and injects
 * {@code X-User-Email}, {@code X-User-Roles} and {@code X-Workspace-Id};
 * {@link RequestIdentity} reads them from the current servlet request. When no
 * headers are present - direct access to the internal port, or the RabbitMQ
 * consumer thread that creates incidents from telemetry - the adapter keeps
 * the historical defaults so those flows behave exactly as before.</p>
 */
@Component
public class JWTSecurityContextAdapter {

    public String getCurrentActor() {
        return RequestIdentity.currentUserEmail().orElse("system-automation");
    }

    public Optional<Long> getCurrentWorkspaceId() {
        return RequestIdentity.currentWorkspaceId();
    }

    /**
     * {@code true} when the gateway reported ROLE_ADMIN. Without gateway
     * headers (background threads, direct internal access) it falls back to
     * the legacy value {@code true}, which disables tenant scoping and keeps
     * existing drills working.
     */
    public boolean isCurrentUserAdmin() {
        if (!RequestIdentity.hasRolesHeader()) {
            return true;
        }
        return RequestIdentity.isAdmin();
    }
}
