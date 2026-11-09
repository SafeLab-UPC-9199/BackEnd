package com.test.backend.alerts.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTSecurityContextAdapterTest {

    private final JWTSecurityContextAdapter adapter = new JWTSecurityContextAdapter();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void withHeaders(String email, String roles, String workspaceId) {
        var request = new MockHttpServletRequest();
        if (email != null) request.addHeader("X-User-Email", email);
        if (roles != null) request.addHeader("X-User-Roles", roles);
        if (workspaceId != null) request.addHeader("X-Workspace-Id", workspaceId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void fallsBackToLegacyDefaultsWithoutGatewayHeaders() {
        // RabbitMQ consumer threads and direct internal-port access have no
        // identity headers: keep the historical behaviour.
        assertEquals("system-automation", adapter.getCurrentActor());
        assertTrue(adapter.getCurrentWorkspaceId().isEmpty());
        assertTrue(adapter.isCurrentUserAdmin());
    }

    @Test
    void readsIdentityInjectedByTheGateway() {
        withHeaders("u202312510@upc.edu.pe", "ROLE_ADMIN,ROLE_USER", "5");

        assertEquals("u202312510@upc.edu.pe", adapter.getCurrentActor());
        assertEquals(5L, adapter.getCurrentWorkspaceId().orElseThrow());
        assertTrue(adapter.isCurrentUserAdmin());
    }

    @Test
    void nonAdminRoleIsNotAdmin() {
        withHeaders("operator@upc.edu.pe", "ROLE_LAB_OPERATOR", "5");

        assertFalse(adapter.isCurrentUserAdmin());
        assertEquals(5L, adapter.getCurrentWorkspaceId().orElseThrow());
    }

    @Test
    void emptyRolesHeaderMeansNotAdmin() {
        withHeaders("user@upc.edu.pe", "", null);

        assertFalse(adapter.isCurrentUserAdmin(), "gateway reported no roles: not admin");
        assertTrue(adapter.getCurrentWorkspaceId().isEmpty());
    }

    @Test
    void malformedWorkspaceHeaderIsIgnored() {
        withHeaders("user@upc.edu.pe", "ROLE_USER", "not-a-number");

        assertTrue(adapter.getCurrentWorkspaceId().isEmpty());
        assertEquals("user@upc.edu.pe", adapter.getCurrentActor());
    }
}
