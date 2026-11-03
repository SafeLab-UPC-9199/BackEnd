package com.test.backend.compliance.domain.model.queries;

/**
 * Query to retrieve audit trail entries.
 * Both filters are optional; when null they are ignored.
 */
public record GetAuditTrailsQuery(String targetId, String action) {
}
