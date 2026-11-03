package com.test.backend.compliance.interfaces.rest.resources;

import java.util.Date;

public record AuditTrailResource(
        Long id,
        String targetId,
        String action,
        String actor,
        String previousState,
        String newState,
        String details,
        String signature,
        Date createdAt
) {}
