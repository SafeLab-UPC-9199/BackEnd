package com.test.backend.compliance.application.internal.queryservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.aggregates.Incident;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.IncidentRepository;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.domain.model.aggregates.Workspace;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditTrailQueryServiceImplTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private AuditTrailQueryServiceImpl service;

    private AuditTrail trailForIncident100;
    private AuditTrail trailForIncident999;

    @BeforeEach
    void setUp() {
        trailForIncident100 = new AuditTrail("100", "RESOLVE_INCIDENT", "tech@safelab.com",
                "ACKNOWLEDGED", "RESOLVED", "Resolved by tech", "abc123");
        trailForIncident999 = new AuditTrail("999", "CREATE_INCIDENT", "other@safelab.com",
                null, "OPEN", "Opened", "def456");
    }

    @Test
    @DisplayName("Admin user gets all trails without workspace filtering (AAA)")
    void handle_ShouldReturnAllTrails_WhenUserIsAdmin() {
        // Arrange
        when(auditTrailRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(trailForIncident100, trailForIncident999));
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);

        // Act
        var result = service.handle(new GetAuditTrailsQuery(null, null));

        // Assert
        assertEquals(2, result.size());
        verify(incidentRepository, never()).findByLaboratoryWorkspaceId(any());
    }

    @Test
    @DisplayName("Non-admin user only sees trails of targets inside own workspace (AAA)")
    void handle_ShouldFilterByWorkspace_WhenUserIsNotAdmin() {
        // Arrange
        var lab = new Laboratory();
        var workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", 10L);
        lab.setWorkspace(workspace);

        var ownIncident = new Incident(lab, "Spike", "Detail", "CRITICAL", 15, "COORDINATOR");
        ReflectionTestUtils.setField(ownIncident, "id", 100L);

        when(auditTrailRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(trailForIncident100, trailForIncident999));
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByLaboratoryWorkspaceId(10L)).thenReturn(List.of(ownIncident));
        when(alertRepository.findByLaboratoryWorkspaceId(10L)).thenReturn(List.of());

        // Act
        var result = service.handle(new GetAuditTrailsQuery(null, null));

        // Assert
        assertEquals(1, result.size());
        assertEquals("100", result.get(0).getTargetId());
        verify(auditTrailRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("targetId filter uses dedicated repository method (AAA)")
    void handle_ShouldUseTargetIdFilter_WhenProvided() {
        // Arrange
        when(auditTrailRepository.findByTargetId("100")).thenReturn(List.of(trailForIncident100));
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetAuditTrailsQuery("100", null));

        // Assert
        assertEquals(1, result.size());
        verify(auditTrailRepository).findByTargetId("100");
        verify(auditTrailRepository, never()).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("action filter uses dedicated repository method (AAA)")
    void handle_ShouldUseActionFilter_WhenProvided() {
        // Arrange
        when(auditTrailRepository.findByAction("RESOLVE_INCIDENT")).thenReturn(List.of(trailForIncident100));
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetAuditTrailsQuery(null, "RESOLVE_INCIDENT"));

        // Assert
        assertEquals(1, result.size());
        verify(auditTrailRepository).findByAction("RESOLVE_INCIDENT");
    }

    @Test
    @DisplayName("targetId and action together use combined repository method (AAA)")
    void handle_ShouldUseCombinedFilter_WhenBothProvided() {
        // Arrange
        when(auditTrailRepository.findByTargetIdAndAction("100", "RESOLVE_INCIDENT"))
                .thenReturn(List.of(trailForIncident100));
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetAuditTrailsQuery("100", "RESOLVE_INCIDENT"));

        // Assert
        assertEquals(1, result.size());
        verify(auditTrailRepository).findByTargetIdAndAction("100", "RESOLVE_INCIDENT");
    }
}
