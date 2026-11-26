package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Incident;
import com.test.backend.alerts.domain.model.commands.*;
import com.test.backend.alerts.domain.services.DuplicateIncidentPolicy;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.IncidentRepository;
import com.test.backend.alerts.infrastructure.security.JWTSecurityContextAdapter;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentCommandServiceImplTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private SharedEventOutboxRepository eventOutboxRepository;

    @Mock
    private DuplicateIncidentPolicy duplicateIncidentPolicy;

    @Mock
    private JWTSecurityContextAdapter jwtSecurityContextAdapter;

    @InjectMocks
    private IncidentCommandServiceImpl service;

    private Incident sampleIncident(Long id) {
        var incident = new Incident(1L, "Spike", "Detail", "CRITICAL", 15, "COORDINATOR");
        incident.setLaboratoryWorkspaceId(10L);
        ReflectionTestUtils.setField(incident, "id", id);
        return incident;
    }

    @Test
    @DisplayName("Persists incident aggregate and event outbox when command is valid")
    void shouldPersistIncidentAndOutboxWhenCommandIsValid() {
        var command = new CreateIncidentCommand(1L, "Critical Freeze", "Temperature dropped to -10C", "CRITICAL", 15, "COORDINATOR");
        when(duplicateIncidentPolicy.hasActiveIncident(1L)).thenReturn(false);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> {
            Incident inc = i.getArgument(0);
            ReflectionTestUtils.setField(inc, "id", 100L);
            return inc;
        });

        var result = service.handle(command);

        assertTrue(result.isPresent());
        assertEquals("OPEN", result.get().getStatus());
        assertEquals("CRITICAL", result.get().getSeverity());
        assertEquals(10L, result.get().getLaboratoryWorkspaceId());
        verify(incidentRepository).save(any(Incident.class));
        verify(eventOutboxRepository).save(argThat(outbox ->
                "INCIDENT_CREATED".equals(outbox.getEventType())
                        && "10".equals(outbox.getOrganizationId())
                        && outbox.getCorrelationId() != null
                        && !outbox.getCorrelationId().isBlank()
                        && SharedEventOutbox.DEFAULT_SCHEMA_VERSION.equals(outbox.getSchemaVersion())));
    }

    @Test
    @DisplayName("Transitions status to ACKNOWLEDGED and emits outbox event")
    void shouldTransitionToAcknowledgedAndEmitEvent() {
        var incident = sampleIncident(200L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(200L, 10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.handle(new AcknowledgeIncidentCommand(200L));

        assertTrue(result.isPresent());
        assertEquals("ACKNOWLEDGED", result.get().getStatus());
        verify(eventOutboxRepository).save(argThat(outbox -> "INCIDENT_ACKNOWLEDGED".equals(outbox.getEventType())));
    }

    @Test
    @DisplayName("Transitions status to IN_PROGRESS on attend")
    void shouldTransitionToInProgressOnAttend() {
        var incident = sampleIncident(250L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(250L, 10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.handle(new AttendIncidentCommand(250L));

        assertTrue(result.isPresent());
        assertEquals("IN_PROGRESS", result.get().getStatus());
        verify(eventOutboxRepository).save(argThat(outbox -> "INCIDENT_ATTENDED".equals(outbox.getEventType())));
    }

    @Test
    @DisplayName("Attaches corrective action and transitions status to RESOLVED")
    void shouldAttachCorrectiveActionAndTransitionToResolved() {
        var incident = sampleIncident(300L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(300L, 10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.handle(new ResolveIncidentCommand(300L, "Replaced faulty thermocouple sensor in rack A", 55L));

        assertTrue(result.isPresent());
        assertEquals("RESOLVED", result.get().getStatus());
        assertNotNull(result.get().getCorrectiveAction());
        assertEquals("Replaced faulty thermocouple sensor in rack A", result.get().getCorrectiveAction().getActionTaken());
        assertNotNull(result.get().getResolvedAt());
        verify(eventOutboxRepository).save(argThat(outbox -> "INCIDENT_RESOLVED".equals(outbox.getEventType())));
    }

    @Test
    @DisplayName("Transitions resolved incident to CLOSED")
    void shouldTransitionResolvedIncidentToClosed() {
        var incident = sampleIncident(350L);
        incident.setStatus("RESOLVED");
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(350L, 10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.handle(new CloseIncidentCommand(350L));

        assertTrue(result.isPresent());
        assertEquals("CLOSED", result.get().getStatus());
        verify(eventOutboxRepository).save(argThat(outbox -> "INCIDENT_CLOSED".equals(outbox.getEventType())));
    }

    @Test
    @DisplayName("Updates status to ESCALATED and records next role")
    void shouldUpdateStatusToEscalatedAndRecordNextRole() {
        var incident = sampleIncident(400L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(400L, 10L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.handle(new ApplyEscalationCommand(400L, "DIRECTOR_OF_QUALITY"));

        assertTrue(result.isPresent());
        assertEquals("ESCALATED", result.get().getStatus());
        assertEquals("DIRECTOR_OF_QUALITY", result.get().getEscalationNextRole());
        verify(eventOutboxRepository).save(argThat(outbox -> "INCIDENT_ESCALATED".equals(outbox.getEventType())));
    }

    @Test
    @DisplayName("Throws IllegalStateException when active incident already exists for the laboratory")
    void shouldThrowIllegalStateExceptionWhenDuplicateActiveIncidentExists() {
        var command = new CreateIncidentCommand(1L, "Excursion", "Temp spike", "CRITICAL", 15, "COORDINATOR");
        when(duplicateIncidentPolicy.hasActiveIncident(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.handle(command));
        verify(incidentRepository, never()).save(any());
        verify(eventOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when laboratoryId is null")
    void shouldThrowIllegalArgumentExceptionWhenLabIdIsNull() {
        var command = new CreateIncidentCommand(null, "Title", "Desc", "CRITICAL", 15, "COORDINATOR");

        assertThrows(IllegalArgumentException.class, () -> service.handle(command));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when corrective action text is shorter than 10 characters")
    void shouldThrowIllegalArgumentExceptionWhenActionTakenIsTooShort() {
        var incident = sampleIncident(300L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(300L, 10L)).thenReturn(Optional.of(incident));

        assertThrows(IllegalArgumentException.class, () ->
                service.handle(new ResolveIncidentCommand(300L, "Fixed it", 55L))
        );
        verify(incidentRepository, never()).save(any());
        verify(eventOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws IllegalStateException when attempting to acknowledge an already resolved incident")
    void shouldThrowIllegalStateExceptionWhenAcknowledgingResolvedIncident() {
        var incident = sampleIncident(200L);
        incident.setStatus("RESOLVED");
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(200L, 10L)).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class, () -> service.handle(new AcknowledgeIncidentCommand(200L)));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws IllegalStateException when attempting to escalate an already resolved incident")
    void shouldThrowIllegalStateExceptionWhenEscalatingResolvedIncident() {
        var incident = sampleIncident(400L);
        incident.setStatus("RESOLVED");
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(400L, 10L)).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class, () -> service.handle(new ApplyEscalationCommand(400L, "DIRECTOR")));
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Returns empty optional when accessing an incident from another workspace")
    void shouldReturnEmptyWhenAccessingCrossWorkspaceIncident() {
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(incidentRepository.findByIdAndLaboratoryWorkspaceId(999L, 10L)).thenReturn(Optional.empty());

        var result = service.handle(new AcknowledgeIncidentCommand(999L));

        assertTrue(result.isEmpty());
        verify(incidentRepository, never()).save(any());
        verify(eventOutboxRepository, never()).save(any());
    }
}
