package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.commands.CreateAlertCommand;
import com.test.backend.alerts.domain.model.commands.DeleteAlertCommand;
import com.test.backend.alerts.domain.model.commands.UpdateAlertCommand;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
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
class AlertCommandServiceImplTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SharedEventOutboxRepository eventOutboxRepository;

    @Mock
    private JWTSecurityContextAdapter jwtSecurityContextAdapter;

    @InjectMocks
    private AlertCommandServiceImpl service;

    @Test
    @DisplayName("handle(CreateAlertCommand) persists alert with workspace id")
    void handle_CreateAlert_ShouldPersistAndReturnAlert_WhenValid() {
        var command = new CreateAlertCommand(
                "Temperature Breach",
                "ULT Freezer temperature exceeded -75°C",
                "Critical",
                "Active",
                "Bio-Safety Lab 04",
                "Just now",
                1L
        );

        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(1L));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Alert> result = service.handle(command);

        assertTrue(result.isPresent());
        assertEquals("Temperature Breach", result.get().getTitle());
        assertEquals(1L, result.get().getLaboratoryId());
        assertEquals(1L, result.get().getLaboratoryWorkspaceId());
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    @DisplayName("handle(DeleteAlertCommand) deletes alert when it exists in current workspace")
    void handle_DeleteAlert_ShouldInvokeRepositoryDelete_WhenExists() {
        var command = new DeleteAlertCommand(5L);
        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(1L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        var alert = new Alert();
        when(alertRepository.findByIdAndLaboratoryWorkspaceId(5L, 1L)).thenReturn(Optional.of(alert));

        service.handle(command);

        verify(alertRepository).findByIdAndLaboratoryWorkspaceId(5L, 1L);
        verify(alertRepository).delete(alert);
    }

    @Test
    @DisplayName("handle(UpdateAlertCommand) publishes outbox event when status changes")
    void handle_UpdateAlert_ShouldWriteOutbox_WhenStatusChanges() {
        var alert = new Alert();
        ReflectionTestUtils.setField(alert, "id", 7L);
        alert.setTitle("Temperature Breach");
        alert.setStatus("ACTIVE");
        alert.setLabName("Bio-Safety Lab 04");
        alert.setLaboratoryWorkspaceId(1L);

        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(1L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(jwtSecurityContextAdapter.getCurrentActor()).thenReturn("tech@safelab.com");
        when(alertRepository.findByIdAndLaboratoryWorkspaceId(7L, 1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = new UpdateAlertCommand(7L, "Temperature Breach", "desc", "CRITICAL", "RESOLVED",
                "Bio-Safety Lab 04", "Just now", null);

        var result = service.handle(command);

        assertTrue(result.isPresent());
        assertEquals("RESOLVED", result.get().getStatus());
        verify(eventOutboxRepository).save(argThat((SharedEventOutbox outbox) ->
                "ALERT_STATUS_UPDATED".equals(outbox.getEventType())
                        && "7".equals(outbox.getAggregateId())
                        && "1".equals(outbox.getOrganizationId())));
    }

    @Test
    @DisplayName("handle(UpdateAlertCommand) does NOT publish outbox when status is unchanged")
    void handle_UpdateAlert_ShouldNotWriteOutbox_WhenStatusUnchanged() {
        var alert = new Alert();
        ReflectionTestUtils.setField(alert, "id", 8L);
        alert.setTitle("Temperature Breach");
        alert.setStatus("ACTIVE");

        when(jwtSecurityContextAdapter.getCurrentWorkspaceId()).thenReturn(Optional.of(1L));
        when(jwtSecurityContextAdapter.isCurrentUserAdmin()).thenReturn(false);
        when(alertRepository.findByIdAndLaboratoryWorkspaceId(8L, 1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        var command = new UpdateAlertCommand(8L, "Temperature Breach", "new description", "CRITICAL", "ACTIVE",
                "Bio-Safety Lab 04", "Just now", null);

        var result = service.handle(command);

        assertTrue(result.isPresent());
        verify(eventOutboxRepository, never()).save(any());
    }
}
