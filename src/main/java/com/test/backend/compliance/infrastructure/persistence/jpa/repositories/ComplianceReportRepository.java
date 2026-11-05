package com.test.backend.compliance.infrastructure.persistence.jpa.repositories;

import com.test.backend.compliance.domain.model.aggregates.ComplianceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, Long> {
    List<ComplianceReport> findByLaboratoryId(Long laboratoryId);

    /** Workspace-scoped variant (QAS-05): only reports whose laboratory belongs to the workspace. */
    List<ComplianceReport> findByLaboratoryIdAndLaboratoryWorkspaceId(Long laboratoryId, Long workspaceId);
}
