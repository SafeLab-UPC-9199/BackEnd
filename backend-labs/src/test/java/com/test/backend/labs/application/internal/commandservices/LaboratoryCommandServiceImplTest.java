package com.test.backend.labs.application.internal.commandservices;

import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.domain.model.aggregates.Workspace;
import com.test.backend.labs.domain.model.commands.CreateLaboratoryCommand;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaboratoryCommandServiceImplTest {

    private LaboratoryRepository laboratoryRepository;
    private WorkspaceRepository workspaceRepository;
    private LaboratoryCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        laboratoryRepository = Mockito.mock(LaboratoryRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        service = new LaboratoryCommandServiceImpl(laboratoryRepository, workspaceRepository);
        when(laboratoryRepository.findByLabCode("LAB-TEST-01")).thenReturn(Optional.empty());
        when(laboratoryRepository.save(any(Laboratory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void withWorkspaceHeader(String workspaceId) {
        var request = new MockHttpServletRequest();
        if (workspaceId != null) {
            request.addHeader("X-Workspace-Id", workspaceId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private CreateLaboratoryCommand command() {
        return new CreateLaboratoryCommand(
                "Lab Test", "BIO", "OPERATIONAL", "A", "1", "LAB-TEST-01",
                "101", "test", "OPERATIONAL", true, true, null, 30, null, null);
    }

    @Test
    void usesWorkspaceReportedByTheGateway() {
        withWorkspaceHeader("5");
        var workspace = new Workspace("Tenant", "tenant-code");
        when(workspaceRepository.findById(5L)).thenReturn(Optional.of(workspace));

        var lab = service.handle(command()).orElseThrow();

        assertEquals(workspace, lab.getWorkspace());
        verify(workspaceRepository, never()).findByCode(any());
    }

    @Test
    void fallsBackToSeededBaseWorkspaceWithoutClaim() {
        withWorkspaceHeader(null);
        var base = new Workspace(Workspace.BASE_NAME, Workspace.BASE_CODE);
        when(workspaceRepository.findByCode(Workspace.BASE_CODE)).thenReturn(Optional.of(base));

        var lab = service.handle(command()).orElseThrow();

        assertEquals(base, lab.getWorkspace());
        verify(workspaceRepository, never()).findById(any());
    }

    @Test
    void rejectsWorkspaceThatDoesNotExist() {
        withWorkspaceHeader("42");
        when(workspaceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.handle(command()));
    }

    @Test
    void failsWhenNoWorkspaceIsAvailable() {
        withWorkspaceHeader(null);
        when(workspaceRepository.findByCode(Workspace.BASE_CODE)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.handle(command()));
    }

    @Test
    void duplicateLabCodeStillRejected() {
        when(laboratoryRepository.findByLabCode("LAB-TEST-01"))
                .thenReturn(Optional.of(new Laboratory()));

        assertThrows(IllegalArgumentException.class, () -> service.handle(command()));
    }
}
