package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.compliance.domain.model.aggregates.ComplianceReport;
import com.test.backend.compliance.domain.model.queries.GetComplianceReportsByLaboratoryIdQuery;
import com.test.backend.compliance.domain.services.ComplianceReportQueryService;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.ComplianceReportRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComplianceReportQueryServiceImpl implements ComplianceReportQueryService {

    private final ComplianceReportRepository complianceReportRepository;
    private final CurrentWorkspaceService currentWorkspaceService;

    public ComplianceReportQueryServiceImpl(ComplianceReportRepository complianceReportRepository,
                                            CurrentWorkspaceService currentWorkspaceService) {
        this.complianceReportRepository = complianceReportRepository;
        this.currentWorkspaceService = currentWorkspaceService;
    }

    @Override
    public List<ComplianceReport> handle(GetComplianceReportsByLaboratoryIdQuery query) {
        // QAS-05: the laboratoryId comes from the client, so it must be validated
        // against the caller's workspace. Administrators bypass the filter.
        if (currentWorkspaceService.isCurrentUserAdmin()) {
            return complianceReportRepository.findByLaboratoryId(query.laboratoryId());
        }
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            // Fail closed: a non-admin without a workspace sees nothing.
            return List.of();
        }
        return complianceReportRepository.findByLaboratoryIdAndLaboratoryWorkspaceId(
                query.laboratoryId(), workspaceIdOpt.get());
    }
}
