package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.compliance.domain.model.aggregates.ComplianceReport;
import com.test.backend.compliance.domain.model.queries.GetComplianceReportsByLaboratoryIdQuery;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.ComplianceReportRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceReportQueryServiceImplTest {

    @Mock
    private ComplianceReportRepository complianceReportRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private ComplianceReportQueryServiceImpl service;

    @Test
    @DisplayName("non-admin queries are scoped to the caller's workspace (QAS-05)")
    void shouldScopeReportsToCallerWorkspace() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        var report = new ComplianceReport();
        when(complianceReportRepository.findByLaboratoryIdAndLaboratoryWorkspaceId(5L, 10L))
                .thenReturn(List.of(report));

        // Act
        var result = service.handle(new GetComplianceReportsByLaboratoryIdQuery(5L));

        // Assert - the client-provided laboratoryId is validated against the workspace
        assertEquals(1, result.size());
        verify(complianceReportRepository).findByLaboratoryIdAndLaboratoryWorkspaceId(5L, 10L);
        verify(complianceReportRepository, never()).findByLaboratoryId(any());
    }

    @Test
    @DisplayName("administrators keep the global view (admin bypass preserved)")
    void adminShouldSeeEveryWorkspace() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        var report = new ComplianceReport();
        when(complianceReportRepository.findByLaboratoryId(5L)).thenReturn(List.of(report));

        // Act
        var result = service.handle(new GetComplianceReportsByLaboratoryIdQuery(5L));

        // Assert
        assertEquals(1, result.size());
        verify(complianceReportRepository).findByLaboratoryId(5L);
        verify(complianceReportRepository, never()).findByLaboratoryIdAndLaboratoryWorkspaceId(any(), any());
    }

    @Test
    @DisplayName("non-admin without workspace fails closed with an empty result (QAS-05)")
    void shouldFailClosedWhenNoWorkspace() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetComplianceReportsByLaboratoryIdQuery(5L));

        // Assert
        assertTrue(result.isEmpty());
        verifyNoInteractions(complianceReportRepository);
    }
}
