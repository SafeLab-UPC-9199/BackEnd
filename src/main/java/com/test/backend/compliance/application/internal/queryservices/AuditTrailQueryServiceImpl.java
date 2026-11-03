package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.IncidentRepository;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AuditTrailQueryServiceImpl implements AuditTrailQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final AuditTrailRepository auditTrailRepository;
    private final IncidentRepository incidentRepository;
    private final AlertRepository alertRepository;
    private final CurrentWorkspaceService currentWorkspaceService;

    public AuditTrailQueryServiceImpl(
            AuditTrailRepository auditTrailRepository,
            IncidentRepository incidentRepository,
            AlertRepository alertRepository,
            CurrentWorkspaceService currentWorkspaceService
    ) {
        this.auditTrailRepository = auditTrailRepository;
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.currentWorkspaceService = currentWorkspaceService;
    }

    @Override
    public List<AuditTrail> handle(GetAuditTrailsQuery query) {
        List<AuditTrail> trails = loadTrails(query);

        // Workspace scoping: non-admin users only see entries whose target
        // (incident/alert) belongs to their own workspace.
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent() && !currentWorkspaceService.isCurrentUserAdmin()) {
            Set<String> allowedTargets = allowedTargetIds(workspaceIdOpt.get());
            trails = trails.stream()
                    .filter(t -> t.getTargetId() != null && allowedTargets.contains(t.getTargetId()))
                    .collect(Collectors.toList());
        }

        return trails;
    }

    private List<AuditTrail> loadTrails(GetAuditTrailsQuery query) {
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

    private Set<String> allowedTargetIds(Long workspaceId) {
        Set<String> targets = incidentRepository.findByLaboratoryWorkspaceId(workspaceId).stream()
                .map(i -> Objects.toString(i.getId(), null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        alertRepository.findByLaboratoryWorkspaceId(workspaceId).stream()
                .map(a -> Objects.toString(a.getId(), null))
                .filter(Objects::nonNull)
                .forEach(targets::add);
        return targets;
    }
}
