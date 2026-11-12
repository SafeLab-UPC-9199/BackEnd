package com.test.backend.shared.interfaces.rest;

import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetrySimulationControllerTest {

    @Mock
    private ObjectProvider<MqttClient> mqttClientProvider;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @Mock
    private SensorConfigurationRepository sensorConfigurationRepository;

    @InjectMocks
    private TelemetrySimulationController controller;

    private static Map<String, Object> body() {
        return Map.of("unit", "°C", "metric", "temperature", "value", 5.4);
    }

    @Test
    @DisplayName("returns 403 for non-administrators - endpoint was previously permitAll (QAS-05)")
    void simulate_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.simulateTelemetry(body());

        // Assert
        assertEquals(403, response.getStatusCode().value());
        assertEquals("ERROR", response.getBody().get("status"));
        verifyNoInteractions(sensorConfigurationRepository, mqttClientProvider);
    }

    @Test
    @DisplayName("rejects units that are not registered in the caller's workspace (QAS-05)")
    void simulate_ShouldRejectForeignUnit() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(sensorConfigurationRepository.existsByLaboratoryWorkspaceIdAndUnit(10L, "°C")).thenReturn(false);

        // Act
        var response = controller.simulateTelemetry(body());

        // Assert - fail closed, no MQTT publish attempted
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("°C"));
        verifyNoInteractions(mqttClientProvider);
    }

    @Test
    @DisplayName("admits a known unit but reports MQTT as unavailable when disabled (503)")
    void simulate_ShouldReachMqttCheck_ForKnownUnit() {
        // Arrange - MQTT is disabled in this deployment, so getIfAvailable() returns null
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(sensorConfigurationRepository.existsByLaboratoryWorkspaceIdAndUnit(10L, "°C")).thenReturn(true);

        // Act
        var response = controller.simulateTelemetry(body());

        // Assert - the tenant checks passed; only MQTT availability blocks the publish
        assertEquals(503, response.getStatusCode().value());
        verify(mqttClientProvider).getIfAvailable();
    }
}
