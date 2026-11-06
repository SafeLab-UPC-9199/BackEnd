package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuditTrailQueryServiceImpl implements AuditTrailQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AuditTrailRepository auditTrailRepository;

    public AuditTrailQueryServiceImpl(AuditTrailRepository auditTrailRepository) {
        this.auditTrailRepository = auditTrailRepository;
    }

    @Override
    public List<AuditTrail> handle(GetAuditTrailsQuery query) {
        boolean hasTarget = query.targetId() != null && !query.targetId().isBlank();
        boolean hasAction = query.action() != null && !query.action().isBlank();

        if (hasTarget && hasAction) {
            return auditTrailRepository.findByTargetIdAndAction(query.targetId(), query.action());
        }
        if (hasTarget) {
            return auditTrailRepository.findByTargetId(query.targetId());
        }
        if (hasAction) {
            return auditTrailRepository.findByAction(query.action());
        }
        return auditTrailRepository.findAll(NEWEST_FIRST);
    }
}
