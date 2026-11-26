package com.test.backend.compliance.interfaces.rest.resources;

import java.util.Date;

public record AuditTrailResource(
        Long id,
        String targetId,
        String targetType,
        String action,
        String actor,
        String previousState,
        String newState,
        Long organizationId,
        String ipAddress,
        String details,
        String signature,
        Date createdAt
) {
    public AuditTrailResource(Long id, String targetId, String action, String actor,
                              String previousState, String newState, String details,
                              String signature, Date createdAt) {
        this(id, targetId, null, action, actor, previousState, newState, null, null, details, signature, createdAt);
    }
}
