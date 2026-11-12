package com.test.backend.compliance.infrastructure.persistence.jpa.repositories;

import com.test.backend.compliance.domain.model.aggregates.ComplianceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, Long> {
    // Workspace-scoped variant (QAS-05 findByLaboratoryIdAndLaboratoryWorkspaceId)
    // was dropped in the microservices split: ComplianceReport only stores a flat
    // laboratoryId and the module query service filters by laboratoryId.
    List<ComplianceReport> findByLaboratoryId(Long laboratoryId);
}
