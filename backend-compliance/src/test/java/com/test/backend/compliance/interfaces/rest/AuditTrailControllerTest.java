package com.test.backend.compliance.interfaces.rest;

import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditTrailControllerTest {

    private AuditTrailQueryService queryService;
    private AuditTrailController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(AuditTrailQueryService.class);
        controller = new AuditTrailController(queryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void nonAdminForcesWorkspaceOrganizationId() {
        withIdentity("ROLE_USER", "10");
        when(queryService.handle(any())).thenReturn(List.of());

        var response = controller.getAuditTrails(null, null, "alice@safelab.io", null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        var captor = ArgumentCaptor.forClass(GetAuditTrailsQuery.class);
        verify(queryService).handle(captor.capture());
        assertEquals(10L, captor.getValue().organizationId());
        assertEquals("alice@safelab.io", captor.getValue().user());
    }

    @Test
    void nonAdminCrossOrganizationQueryReturnsForbidden() {
        withIdentity("ROLE_USER", "10");

        var response = controller.getAuditTrails(null, null, null, 99L, null, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(queryService);
    }

    @Test
    void nonAdminWithoutWorkspaceReturnsForbidden() {
        withIdentity("ROLE_USER", null);

        var response = controller.getAuditTrails(null, null, null, null, null, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(queryService);
    }

    @Test
    void adminCanQueryAnyOrAllOrganizations() {
        withIdentity("ROLE_ADMIN", "10");
        when(queryService.handle(any())).thenReturn(List.of());

        assertEquals(HttpStatus.OK, controller.getAuditTrails(null, null, null, 99L, null, null).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getAuditTrails(null, null, null, null, null, null).getStatusCode());

        var captor = ArgumentCaptor.forClass(GetAuditTrailsQuery.class);
        verify(queryService, org.mockito.Mockito.times(2)).handle(captor.capture());
        assertEquals(99L, captor.getAllValues().get(0).organizationId());
        assertNull(captor.getAllValues().get(1).organizationId());
    }

    @Test
    void httpEndpointBindsFiltersAndEnforcesCrossOrg403() throws Exception {
        var entry = new AuditTrail("alice@safelab.io", "SECURITY", "security.login.succeeded",
                "alice@safelab.io", 10L, "10.0.0.1", "ok");
        when(queryService.handle(any())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/audit-trails")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .param("user", "alice@safelab.io")
                        .param("organizationId", "10")
                        .param("from", "2026-11-26T00:00:00Z")
                        .param("to", "2026-11-26T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("alice@safelab.io"))
                .andExpect(jsonPath("$[0].organizationId").value(10))
                .andExpect(jsonPath("$[0].targetType").value("SECURITY"));

        var captor = ArgumentCaptor.forClass(GetAuditTrailsQuery.class);
        verify(queryService).handle(captor.capture());
        assertEquals(Instant.parse("2026-11-26T00:00:00Z"), captor.getValue().from());
        assertEquals(Instant.parse("2026-11-26T23:59:59Z"), captor.getValue().to());

        mockMvc.perform(get("/api/v1/audit-trails")
                        .header("X-User-Roles", "ROLE_USER")
                        .header("X-Workspace-Id", "10")
                        .param("organizationId", "20"))
                .andExpect(status().isForbidden());
    }

    private void withIdentity(String roles, String workspaceId) {
        var request = new MockHttpServletRequest();
        if (roles != null) request.addHeader("X-User-Roles", roles);
        if (workspaceId != null) request.addHeader("X-Workspace-Id", workspaceId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
