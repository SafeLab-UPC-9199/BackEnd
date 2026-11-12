package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.PendingInvitation;
import com.test.backend.automation.domain.model.aggregates.UserProfile;
import com.test.backend.automation.domain.model.commands.CreatePendingInvitationCommand;
import com.test.backend.automation.domain.model.entities.Role;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.PendingInvitationRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.domain.model.aggregates.Workspace;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.WorkspaceRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingInvitationControllerTest {

    @Mock
    private PendingInvitationRepository pendingInvitationRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private UserWorkspaceAccessRepository userWorkspaceAccessRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @InjectMocks
    private PendingInvitationController controller;

    private Role role() {
        var role = new Role();
        role.setName("User");
        return role;
    }

    private Workspace workspace(Long id) {
        var workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    @Test
    @DisplayName("creating an invitation with a laboratory from another workspace returns 400 (QAS-05)")
    void createInvitation_ShouldRejectForeignLaboratory() {
        // Arrange
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(roleRepository.findByName("User")).thenReturn(Optional.of(role()));
        when(laboratoryRepository.findByIdAndWorkspaceId(2L, 10L)).thenReturn(Optional.empty());

        // Act
        var response = controller.createInvitation(
                new CreatePendingInvitationCommand("invitee@safelab.com", "User", List.of(2L)));

        // Assert - the crafted laboratory id never reaches the stored invitation
        assertEquals(400, response.getStatusCode().value());
        verify(pendingInvitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("creating an invitation with a laboratory of the caller's workspace succeeds (QAS-05)")
    void createInvitation_ShouldAcceptOwnWorkspaceLaboratory() {
        // Arrange
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(roleRepository.findByName("User")).thenReturn(Optional.of(role()));
        var lab = new Laboratory();
        ReflectionTestUtils.setField(lab, "id", 2L);
        when(laboratoryRepository.findByIdAndWorkspaceId(2L, 10L)).thenReturn(Optional.of(lab));
        when(pendingInvitationRepository.save(any(PendingInvitation.class))).thenAnswer(i -> i.getArgument(0));
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace(10L)));

        // Act
        var response = controller.createInvitation(
                new CreatePendingInvitationCommand("invitee@safelab.com", "User", List.of(2L)));

        // Assert
        assertEquals(201, response.getStatusCode().value());
        assertEquals(List.of(2L), response.getBody().laboratoryIds());
        verify(laboratoryRepository).findByIdAndWorkspaceId(2L, 10L);
        verify(laboratoryRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("accepting resolves laboratories inside the invitation's workspace only (QAS-05)")
    void acceptInvitation_ShouldScopeLaboratoriesToInvitationWorkspace() {
        // Arrange
        when(currentWorkspaceService.getCurrentUserEmail()).thenReturn(Optional.of("invitee@safelab.com"));
        var invitation = new PendingInvitation(
                new CreatePendingInvitationCommand("invitee@safelab.com", "User", List.of(2L)), role(), 10L);
        when(pendingInvitationRepository.findById(5L)).thenReturn(Optional.of(invitation));

        var profile = new UserProfile();
        profile.setEmail("invitee@safelab.com");
        ReflectionTestUtils.setField(profile, "id", 1L);
        when(userProfileRepository.findByEmail("invitee@safelab.com")).thenReturn(Optional.of(profile));
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace(10L)));
        when(userWorkspaceAccessRepository.findByUserProfileIdAndWorkspaceId(1L, 10L))
                .thenReturn(Optional.empty());
        when(userWorkspaceAccessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var lab = new Laboratory();
        ReflectionTestUtils.setField(lab, "id", 2L);
        when(laboratoryRepository.findByIdAndWorkspaceId(2L, 10L)).thenReturn(Optional.of(lab));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.acceptInvitation(5L);

        // Assert - only the workspace-qualified lookup is used
        assertEquals(200, response.getStatusCode().value());
        verify(laboratoryRepository).findByIdAndWorkspaceId(2L, 10L);
        verify(laboratoryRepository, never()).findById(anyLong());
        verify(pendingInvitationRepository).delete(invitation);
    }

    @Test
    @DisplayName("a laboratory outside the invitation's workspace is silently not granted (QAS-05)")
    void acceptInvitation_ShouldNotGrantForeignLaboratoryAccess() {
        // Arrange
        when(currentWorkspaceService.getCurrentUserEmail()).thenReturn(Optional.of("invitee@safelab.com"));
        var invitation = new PendingInvitation(
                new CreatePendingInvitationCommand("invitee@safelab.com", "User", List.of(99L)), role(), 10L);
        when(pendingInvitationRepository.findById(5L)).thenReturn(Optional.of(invitation));

        var profile = new UserProfile();
        profile.setEmail("invitee@safelab.com");
        ReflectionTestUtils.setField(profile, "id", 1L);
        when(userProfileRepository.findByEmail("invitee@safelab.com")).thenReturn(Optional.of(profile));
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace(10L)));
        when(userWorkspaceAccessRepository.findByUserProfileIdAndWorkspaceId(1L, 10L))
                .thenReturn(Optional.empty());
        when(userWorkspaceAccessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(laboratoryRepository.findByIdAndWorkspaceId(99L, 10L)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        var response = controller.acceptInvitation(5L);

        // Assert - invitation is consumed but the foreign lab grants nothing
        assertEquals(200, response.getStatusCode().value());
        verify(laboratoryRepository, never()).findById(anyLong());
        assertTrue(profile.getLabAccesses().isEmpty(), "no LabUserAccess may be granted for a foreign laboratory");
    }
}
