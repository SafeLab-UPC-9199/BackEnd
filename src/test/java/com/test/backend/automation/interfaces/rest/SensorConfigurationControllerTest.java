package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.EquipmentThreshold;
import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.EquipmentThresholdRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.automation.interfaces.rest.resources.SensorConfigurationResource;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorConfigurationControllerTest {

    @Mock
    private SensorConfigurationRepository sensorConfigurationRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private EquipmentThresholdRepository equipmentThresholdRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private SensorConfigurationController controller;

    private SensorConfigurationResource createBody(Long laboratoryId, Long equipmentId) {
        return new SensorConfigurationResource(
                null, "Sensor A", "temperature", "°C", null, true, null, null,
                laboratoryId, null, equipmentId, null, null, null, null);
    }

    @Test
    @DisplayName("create returns 400 when the equipment belongs to another workspace (QAS-05)")
    void createConfiguration_ShouldRejectForeignEquipment() {
        // Arrange
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        var lab = new Laboratory();
        ReflectionTestUtils.setField(lab, "id", 1L);
        when(laboratoryRepository.findByIdAndWorkspaceId(1L, 10L)).thenReturn(Optional.of(lab));
        when(equipmentThresholdRepository.findByIdAndLaboratoryWorkspaceId(9L, 10L)).thenReturn(Optional.empty());

        // Act
        var response = controller.createConfiguration(createBody(1L, 9L));

        // Assert - the crafted equipment id is rejected, never attached nor persisted
        assertEquals(400, response.getStatusCode().value());
        verify(sensorConfigurationRepository, never()).save(any());
        verify(equipmentThresholdRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("create succeeds with equipment of the caller's workspace (QAS-05)")
    void createConfiguration_ShouldAcceptOwnWorkspaceEquipment() {
        // Arrange
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        var lab = new Laboratory();
        ReflectionTestUtils.setField(lab, "id", 1L);
        when(laboratoryRepository.findByIdAndWorkspaceId(1L, 10L)).thenReturn(Optional.of(lab));
        var equipment = new EquipmentThreshold();
        equipment.setName("Freezer F1");
        when(equipmentThresholdRepository.findByIdAndLaboratoryWorkspaceId(9L, 10L)).thenReturn(Optional.of(equipment));
        when(sensorConfigurationRepository.save(any(SensorConfiguration.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.createConfiguration(createBody(1L, 9L));

        // Assert
        assertEquals(201, response.getStatusCode().value());
        verify(sensorConfigurationRepository).save(any(SensorConfiguration.class));
    }

    @Test
    @DisplayName("update returns 404 for a configuration of another workspace (QAS-05)")
    void updateConfiguration_ShouldReturnNotFound_ForForeignConfiguration() {
        // Arrange
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(sensorConfigurationRepository.findByIdAndLaboratoryWorkspaceId(5L, 10L)).thenReturn(Optional.empty());

        // Act
        var response = controller.updateConfiguration(5L, createBody(null, null));

        // Assert
        assertEquals(404, response.getStatusCode().value());
        verify(sensorConfigurationRepository, never()).save(any());
        verifyNoInteractions(laboratoryRepository);
    }

    @Test
    @DisplayName("device-secret provisioning returns 403 for non-administrators (firma por dispositivo)")
    void provisionDeviceSecret_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.provisionDeviceSecret(5L);

        // Assert - credentials are Administrator only, repository untouched
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(sensorConfigurationRepository);
    }

    @Test
    @DisplayName("device-secret provisioning stores a fresh 256-bit hex secret and returns it once (firma por dispositivo)")
    void provisionDeviceSecret_ShouldGenerateAndPersistSecret() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        var config = new SensorConfiguration();
        ReflectionTestUtils.setField(config, "id", 5L);
        config.setUnit("Lab-04");
        when(sensorConfigurationRepository.findById(5L)).thenReturn(Optional.of(config));
        when(sensorConfigurationRepository.save(any(SensorConfiguration.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.provisionDeviceSecret(5L);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        String secret = response.getBody().get("deviceSecret");
        assertNotNull(secret);
        assertTrue(secret.matches("[0-9a-f]{64}"), "expected 32 random bytes rendered as hex, got: " + secret);
        assertEquals(secret, config.getDeviceSecret());
        verify(sensorConfigurationRepository).save(config);
    }

    @Test
    @DisplayName("device-secret provisioning returns 404 for an unknown sensor (firma por dispositivo)")
    void provisionDeviceSecret_ShouldReturnNotFound_WhenSensorMissing() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        when(sensorConfigurationRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        var response = controller.provisionDeviceSecret(99L);

        // Assert
        assertEquals(404, response.getStatusCode().value());
        verify(sensorConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("device-secret revocation falls back to the shared secret (firma por dispositivo)")
    void revokeDeviceSecret_ShouldClearTheSecret() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        var config = new SensorConfiguration();
        ReflectionTestUtils.setField(config, "id", 5L);
        config.setDeviceSecret("old-secret");
        when(sensorConfigurationRepository.findById(5L)).thenReturn(Optional.of(config));
        when(sensorConfigurationRepository.save(any(SensorConfiguration.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.revokeDeviceSecret(5L);

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals("REVOKED", response.getBody().get("status"));
        assertNull(config.getDeviceSecret());
        verify(sensorConfigurationRepository).save(config);
    }

    @Test
    @DisplayName("device-secret revocation returns 403 for non-administrators (firma por dispositivo)")
    void revokeDeviceSecret_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.revokeDeviceSecret(5L);

        // Assert
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(sensorConfigurationRepository);
    }
}
