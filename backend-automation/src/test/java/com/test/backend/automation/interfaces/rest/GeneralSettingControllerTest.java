package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.GeneralSetting;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.GeneralSettingRepository;
import com.test.backend.automation.interfaces.rest.resources.GeneralSettingResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * QAS-05: general settings are shared by every tenant, so reads are open to
 * authenticated callers and updates require Administrator (gateway header
 * {@code X-User-Roles}).
 */
class GeneralSettingControllerTest {

    private GeneralSettingRepository repository;
    private GeneralSettingController controller;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(GeneralSettingRepository.class);
        controller = new GeneralSettingController(repository);
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

    private GeneralSettingResource resource() {
        return new GeneralSettingResource(1L, "e2e.gate", "new-value", "system", "E2E probe");
    }

    private GeneralSetting storedSetting() {
        var setting = new GeneralSetting();
        setting.setSettingKey("e2e.gate");
        setting.setValue("old-value");
        setting.setCategory("system");
        setting.setDescription("E2E probe");
        return setting;
    }

    @Test
    void updateIsForbiddenForNonAdmin() {
        withRoles("ROLE_USER");

        var response = controller.updateSetting(1L, resource());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void updateWithoutGatewayHeadersIsForbidden() {
        withRoles(null);

        assertEquals(HttpStatus.FORBIDDEN, controller.updateSetting(1L, resource()).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void adminUpdateOnUnknownSettingIsNotFound() {
        withRoles("ROLE_ADMIN");
        Mockito.when(repository.findById(1L)).thenReturn(Optional.empty());

        var response = controller.updateSetting(1L, resource());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(repository).findById(1L);
    }

    @Test
    void adminUpdatePersistsValueAndDescription() {
        withRoles("ROLE_ADMIN");
        var setting = storedSetting();
        Mockito.when(repository.findById(1L)).thenReturn(Optional.of(setting));
        Mockito.when(repository.save(setting)).thenReturn(setting);

        var response = controller.updateSetting(1L, resource());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-value", response.getBody().value());
        assertEquals("E2E probe", response.getBody().description());
        verify(repository).save(setting);
    }

    @Test
    void readsStayOpenForAuthenticatedCallers() {
        withRoles("ROLE_USER");
        Mockito.when(repository.findAll()).thenReturn(List.of(storedSetting()));

        var response = controller.getAllSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("e2e.gate", response.getBody().get(0).settingKey());
    }
}
