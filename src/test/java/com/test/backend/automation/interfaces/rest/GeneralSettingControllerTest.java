package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.GeneralSetting;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.GeneralSettingRepository;
import com.test.backend.automation.interfaces.rest.resources.GeneralSettingResource;
import com.test.backend.shared.application.CurrentWorkspaceService;
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
class GeneralSettingControllerTest {

    @Mock
    private GeneralSettingRepository generalSettingRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private GeneralSettingController controller;

    private GeneralSettingResource body() {
        return new GeneralSettingResource(null, "session.timeout", "60", "security", null);
    }

    @Test
    @DisplayName("PUT returns 403 for non-administrators - global settings are Administrator only (QAS-05)")
    void updateSetting_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.updateSetting(1L, body());

        // Assert - rejected before touching the database
        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(generalSettingRepository);
    }

    @Test
    @DisplayName("PUT succeeds for administrators (admin bypass preserved)")
    void updateSetting_ShouldUpdate_WhenAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        var setting = new GeneralSetting();
        setting.setSettingKey("session.timeout");
        setting.setValue("30");
        when(generalSettingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(generalSettingRepository.save(any(GeneralSetting.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.updateSetting(1L, body());

        // Assert
        assertEquals(200, response.getStatusCode().value());
        assertEquals("60", response.getBody().value());
        verify(generalSettingRepository).save(setting);
    }
}
