package com.test.backend.iam.interfaces.rest;

import com.test.backend.iam.domain.services.UserCommandService;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private AuthenticationController controller;

    @Test
    @DisplayName("DELETE /clean-test-users returns 403 for non-administrators (QAS-05)")
    void cleanTestUsers_ShouldReturnForbidden_ForNonAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);

        // Act
        var response = controller.cleanTestUsers();

        // Assert - the destructive operation is never triggered
        assertEquals(403, response.getStatusCode().value());
        verify(userCommandService, never()).handleCleanTestUsers();
    }

    @Test
    @DisplayName("DELETE /clean-test-users succeeds for administrators (admin bypass preserved)")
    void cleanTestUsers_ShouldSucceed_WhenAdmin() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);

        // Act
        var response = controller.cleanTestUsers();

        // Assert
        assertEquals(200, response.getStatusCode().value());
        verify(userCommandService).handleCleanTestUsers();
    }
}
