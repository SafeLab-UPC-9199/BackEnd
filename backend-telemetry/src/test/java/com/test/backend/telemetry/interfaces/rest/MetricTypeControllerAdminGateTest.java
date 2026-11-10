package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.telemetry.domain.model.commands.CreateMetricTypeCommand;
import com.test.backend.telemetry.domain.model.commands.DeleteMetricTypeCommand;
import com.test.backend.telemetry.domain.model.commands.UpdateMetricTypeCommand;
import com.test.backend.telemetry.domain.services.MetricTypeCommandService;
import com.test.backend.telemetry.domain.services.MetricTypeQueryService;
import com.test.backend.telemetry.interfaces.rest.resources.CreateMetricTypeResource;
import com.test.backend.telemetry.interfaces.rest.resources.UpdateMetricTypeResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * QAS-05: metric type catalog writes must be Administrator only (the gateway
 * reports the verified roles through the {@code X-User-Roles} header).
 */
class MetricTypeControllerAdminGateTest {

    private MetricTypeCommandService commandService;
    private MetricTypeController controller;

    @BeforeEach
    void setUp() {
        commandService = Mockito.mock(MetricTypeCommandService.class);
        var queryService = Mockito.mock(MetricTypeQueryService.class);
        controller = new MetricTypeController(commandService, queryService);
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

    private CreateMetricTypeResource createResource() {
        return new CreateMetricTypeResource("e2e_gate_metric", "E2E Gate", "Celsius", null, "test");
    }

    private UpdateMetricTypeResource updateResource() {
        return new UpdateMetricTypeResource("e2e_gate_metric", "E2E Gate", "Celsius", null, "test", true);
    }

    @Test
    void createIsForbiddenForNonAdmin() {
        withRoles("ROLE_USER");

        var response = controller.createMetricType(createResource());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(commandService);
    }

    @Test
    void updateIsForbiddenForNonAdmin() {
        withRoles("ROLE_LAB_OPERATOR");

        var response = controller.updateMetricType(1L, updateResource());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(commandService);
    }

    @Test
    void deleteIsForbiddenForNonAdmin() {
        withRoles("ROLE_USER");

        var response = controller.deleteMetricType(1L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(commandService);
    }

    @Test
    void withoutGatewayHeadersWritesAreForbidden() {
        // Direct internal-port access without identity: fail closed.
        withRoles(null);

        assertEquals(HttpStatus.FORBIDDEN, controller.deleteMetricType(1L).getStatusCode());
        verifyNoInteractions(commandService);
    }

    @Test
    void adminCreatePassesTheGate() {
        withRoles("ROLE_ADMIN");
        Mockito.when(commandService.handle(any(CreateMetricTypeCommand.class)))
                .thenReturn(Optional.empty());

        var response = controller.createMetricType(createResource());

        // Gate passed: the command service decided (empty optional -> 400), not a 403.
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(commandService).handle(any(CreateMetricTypeCommand.class));
    }

    @Test
    void adminUpdatePassesTheGate() {
        withRoles("ROLE_ADMIN");
        Mockito.when(commandService.handle(any(UpdateMetricTypeCommand.class)))
                .thenReturn(Optional.empty());

        var response = controller.updateMetricType(1L, updateResource());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(commandService).handle(any(UpdateMetricTypeCommand.class));
    }

    @Test
    void adminDeletePassesTheGate() {
        withRoles("ROLE_ADMIN");

        var response = controller.deleteMetricType(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(commandService).handle(any(DeleteMetricTypeCommand.class));
    }
}
