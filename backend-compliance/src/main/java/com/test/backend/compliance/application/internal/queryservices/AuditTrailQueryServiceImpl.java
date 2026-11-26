package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
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
        return auditTrailRepository.findAll(byFilters(query), NEWEST_FIRST);
    }

    private static Specification<AuditTrail> byFilters(GetAuditTrailsQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(query.targetId())) {
                predicates.add(cb.equal(root.get("targetId"), query.targetId()));
            }
            if (hasText(query.action())) {
                predicates.add(cb.equal(root.get("action"), query.action()));
            }
            if (hasText(query.user())) {
                predicates.add(cb.equal(root.get("actor"), query.user()));
            }
            if (query.organizationId() != null) {
                predicates.add(cb.equal(root.get("organizationId"), query.organizationId()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), Date.from(query.from())));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), Date.from(query.to())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
