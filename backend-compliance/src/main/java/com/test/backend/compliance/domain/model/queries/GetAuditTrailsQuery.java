package com.test.backend.compliance.domain.model.queries;

import java.time.Instant;

public record GetAuditTrailsQuery(
        String targetId,
        String action,
        String user,
        Long organizationId,
        Instant from,
        Instant to
) {
    public GetAuditTrailsQuery(String targetId, String action) {
        this(targetId, action, null, null, null, null);
    }
}
