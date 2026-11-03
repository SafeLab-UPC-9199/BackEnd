package com.test.backend.compliance.domain.services;

import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;

import java.util.List;

public interface AuditTrailQueryService {
    List<AuditTrail> handle(GetAuditTrailsQuery query);
}
