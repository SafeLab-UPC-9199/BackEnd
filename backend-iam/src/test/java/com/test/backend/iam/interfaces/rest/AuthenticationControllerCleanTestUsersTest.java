package com.test.backend.iam.interfaces.rest;

import com.test.backend.iam.domain.services.UserCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * QAS-05: {@code clean-test-users} is a destructive Administrator-only
 * operation (the monolith gated it the same way).
 */
class AuthenticationControllerCleanTestUsersTest {

    private UserCommandService userCommandService;
    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        userCommandService = Mockito.mock(UserCommandService.class);
        controller = new AuthenticationController(userCommandService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void withRoles(String roles) {
        var request = new MockHttpServletRequest();
        if (roles != null) {
            request.addHeader("X-User-Roles", roles);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void cleanTestUsersIsForbiddenForNonAdmin() {
        withRoles("ROLE_USER");

        var response = controller.cleanTestUsers();

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(userCommandService);
    }

    @Test
    void cleanTestUsersWithoutGatewayHeadersIsForbidden() {
        withRoles(null);

        assertEquals(HttpStatus.FORBIDDEN, controller.cleanTestUsers().getStatusCode());
        verifyNoInteractions(userCommandService);
    }

    @Test
    void adminCleanTestUsersRuns() {
        withRoles("ROLE_ADMIN");

        var response = controller.cleanTestUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userCommandService).handleCleanTestUsers();
    }
}
