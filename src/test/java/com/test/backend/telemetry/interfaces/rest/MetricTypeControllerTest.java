package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.shared.application.CurrentWorkspaceService;
import com.test.backend.telemetry.domain.model.aggregates.MetricType;
import com.test.backend.telemetry.domain.model.commands.DeleteMetricTypeCommand;
import com.test.backend.telemetry.domain.services.MetricTypeCommandService;
import com.test.backend.telemetry.domain.services.MetricTypeQueryService;
import com.test.backend.telemetry.interfaces.rest.resources.CreateMetricTypeResource;
import com.test.backend.telemetry.interfaces.rest.resources.UpdateMetricTypeResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricTypeControllerTest {

    @Mock
    private MetricTypeCommandService metricTypeCommandService;

    @Mock
    private MetricTypeQueryService metricTypeQueryService;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private MetricTypeController controller;

    private static final CreateMetricTypeResource CREATE_BODY =
            new CreateMetricTypeResource("co2", "CO2", "%", "cloud", "environment");

    private static final UpdateMetricTypeResource UPDATE_BODY =
            new UpdateMetricTypeResource("co2", "CO2", "%", "cloud", "environment", true);

    @Test
    @DisplayName("POST returns 403 for non-administrators - the catalog is cross-tenant (QAS-05)")
    void create_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.createMetricType(CREATE_BODY);

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(metricTypeCommandService);
    }

    @Test
    @DisplayName("PUT returns 403 for non-administrators (QAS-05)")
    void update_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.updateMetricType(1L, UPDATE_BODY);

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(metricTypeCommandService);
    }

    @Test
    @DisplayName("DELETE returns 403 for non-administrators (QAS-05)")
    void delete_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.deleteMetricType(1L);

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(metricTypeCommandService);
    }

    @Test
    @DisplayName("POST succeeds for administrators (admin bypass preserved)")
    void create_ShouldSucceed_WhenAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        var created = new MetricType();
        created.setKey("co2");
        when(metricTypeCommandService.handle(any(com.test.backend.telemetry.domain.model.commands.CreateMetricTypeCommand.class)))
                .thenReturn(Optional.of(created));

        // Act
        var response = controller.createMetricType(CREATE_BODY);

        // Assert
        assertEquals(201, response.getStatusCode().value());
        assertEquals("co2", response.getBody().key());
    }

    @Test
    @DisplayName("DELETE succeeds for administrators (admin bypass preserved)")
    void delete_ShouldSucceed_WhenAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        doNothing().when(metricTypeCommandService).handle(any(DeleteMetricTypeCommand.class));

        // Act
        var response = controller.deleteMetricType(1L);

        // Assert
        assertEquals(204, response.getStatusCode().value());
    }
}
