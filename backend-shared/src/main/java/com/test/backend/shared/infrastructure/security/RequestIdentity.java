package com.test.backend.shared.infrastructure.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Reads the identity headers injected by the edge gateway (Fase 6, ADR-006).
 *
 * <p>The gateway validates the JWT and forwards the verified claims as
 * {@code X-User-Email}, {@code X-User-Roles} and {@code X-Workspace-Id},
 * removing any copy sent by the client. Services must treat these headers as
 * the caller's identity: the gateway already stripped spoofed values, but
 * direct access to a service port bypasses that check (accepted local
 * trade-off documented in ADR-006).</p>
 *
 * <p>All accessors are static and degrade to {@link Optional#empty()} when
 * there is no servlet request in the thread (RabbitMQ consumers, scheduled
 * dispatchers), so background paths keep working with their own fallbacks.</p>
 */
public final class RequestIdentity {

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";

    /** Role name checked by admin gates; mirrors backend-iam's Roles enum. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private RequestIdentity() {
    }

    /** Email of the authenticated caller ({@code sub} claim), if any. */
    public static Optional<String> currentUserEmail() {
        return nonBlank(header(HEADER_USER_EMAIL));
    }

    /**
     * Workspace (tenant) of the authenticated caller. Empty when the token had
     * no {@code workspaceId} claim (e.g. no user profile yet) or the header is
     * absent on background threads.
     */
    public static Optional<Long> currentWorkspaceId() {
        return nonBlank(header(HEADER_WORKSPACE_ID)).flatMap(value -> {
            try {
                return Optional.of(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /** Roles reported by the gateway; empty list when the header is absent. */
    public static List<String> currentRoles() {
        var raw = header(HEADER_USER_ROLES);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    /**
     * {@code true} only when the gateway explicitly reported ROLE_ADMIN. An
     * absent header (no gateway in the path) returns {@code false}: callers
     * decide how to treat "unknown identity"; background threads never claim
     * to be admin.
     */
    public static boolean isAdmin() {
        return currentRoles().contains(ROLE_ADMIN);
    }

    /** Raw presence check: distinguishes "header sent empty" from "absent". */
    public static boolean hasRolesHeader() {
        return header(HEADER_USER_ROLES) != null;
    }

    private static Optional<String> nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static String header(String name) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(name);
        }
        return null;
    }
}
