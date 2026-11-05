package com.test.backend.iam.application.internal.queryservices;

import com.test.backend.automation.domain.model.aggregates.UserProfile;
import com.test.backend.automation.domain.model.entities.UserWorkspaceAccess;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.queries.GetAllUsersQuery;
import com.test.backend.iam.domain.model.queries.GetUserByIdQuery;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserWorkspaceAccessRepository userWorkspaceAccessRepository;

    @Mock
    private CurrentWorkspaceService currentWorkspaceService;

    @InjectMocks
    private UserQueryServiceImpl service;

    @Captor
    private ArgumentCaptor<Collection<String>> emailsCaptor;

    private User userWith(Long id, String email) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        return user;
    }

    private UserProfile profileWith(String email, Long workspaceId) {
        var profile = new UserProfile();
        profile.setEmail(email);
        profile.setWorkspaceId(workspaceId);
        return profile;
    }

    @Test
    @DisplayName("directory listing only returns members of the caller's workspace (QAS-05)")
    void shouldListOnlyWorkspaceMembers() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(userProfileRepository.findByWorkspaceId(10L))
                .thenReturn(List.of(profileWith("member@safelab.com", 10L)));
        when(userWorkspaceAccessRepository.findByWorkspaceId(10L)).thenReturn(List.of());
        var member = userWith(1L, "member@safelab.com");
        when(userRepository.findByEmailIn(any())).thenReturn(List.of(member));

        // Act
        var result = service.handle(new GetAllUsersQuery());

        // Assert
        assertEquals(1, result.size());
        assertEquals("member@safelab.com", result.get(0).getEmail());
        verify(userRepository).findByEmailIn(emailsCaptor.capture());
        assertTrue(emailsCaptor.getValue().contains("member@safelab.com"));
        verify(userRepository, never()).findAll();
    }

    @Test
    @DisplayName("directory listing includes users granted access via UserWorkspaceAccess")
    void shouldIncludeWorkspaceAccessMembers() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        when(userProfileRepository.findByWorkspaceId(10L)).thenReturn(List.of());
        var foreignProfile = profileWith("guest@safelab.com", 99L);
        var access = new UserWorkspaceAccess();
        access.setUserProfile(foreignProfile);
        when(userWorkspaceAccessRepository.findByWorkspaceId(10L)).thenReturn(List.of(access));
        when(userRepository.findByEmailIn(any())).thenReturn(List.of(userWith(2L, "guest@safelab.com")));

        // Act
        var result = service.handle(new GetAllUsersQuery());

        // Assert
        assertEquals(1, result.size());
        verify(userRepository).findByEmailIn(emailsCaptor.capture());
        assertTrue(emailsCaptor.getValue().contains("guest@safelab.com"));
    }

    @Test
    @DisplayName("administrators keep the global directory (admin bypass preserved)")
    void adminShouldSeeEveryUser() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        when(userRepository.findAll()).thenReturn(List.of(userWith(1L, "a@safelab.com"), userWith(2L, "b@safelab.com")));

        // Act
        var result = service.handle(new GetAllUsersQuery());

        // Assert
        assertEquals(2, result.size());
        verify(userRepository).findAll();
        verifyNoInteractions(userProfileRepository, userWorkspaceAccessRepository);
    }

    @Test
    @DisplayName("non-admin without workspace fails closed with an empty directory (QAS-05)")
    void shouldFailClosedWhenNoWorkspace() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetAllUsersQuery());

        // Assert
        assertTrue(result.isEmpty());
        verify(userRepository, never()).findAll();
        verify(userRepository, never()).findByEmailIn(any());
    }

    @Test
    @DisplayName("byId returns a record only when the user is a member of the caller's workspace (QAS-05)")
    void byIdShouldDenyForeignUsers() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        var foreign = userWith(7L, "foreign@safelab.com");
        when(userRepository.findById(7L)).thenReturn(Optional.of(foreign));
        when(userProfileRepository.findByEmail("foreign@safelab.com"))
                .thenReturn(Optional.of(profileWith("foreign@safelab.com", 99L)));
        when(userWorkspaceAccessRepository.findByUserProfileEmailAndWorkspaceId("foreign@safelab.com", 10L))
                .thenReturn(Optional.empty());

        // Act
        var result = service.handle(new GetUserByIdQuery(7L));

        // Assert - hidden from the caller as if it did not exist (404)
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("byId returns a record for a member of the caller's workspace")
    void byIdShouldReturnOwnWorkspaceMember() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(false);
        when(currentWorkspaceService.getCurrentWorkspaceId()).thenReturn(Optional.of(10L));
        var member = userWith(7L, "member@safelab.com");
        when(userRepository.findById(7L)).thenReturn(Optional.of(member));
        when(userProfileRepository.findByEmail("member@safelab.com"))
                .thenReturn(Optional.of(profileWith("member@safelab.com", 10L)));

        // Act
        var result = service.handle(new GetUserByIdQuery(7L));

        // Assert
        assertTrue(result.isPresent());
        assertEquals("member@safelab.com", result.get().getEmail());
    }

    @Test
    @DisplayName("byId keeps the administrator bypass (admin bypass preserved)")
    void byIdShouldLetAdminsReadAnyUser() {
        // Arrange
        when(currentWorkspaceService.isCurrentUserAdmin()).thenReturn(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(userWith(7L, "any@safelab.com")));

        // Act
        var result = service.handle(new GetUserByIdQuery(7L));

        // Assert
        assertTrue(result.isPresent());
        verifyNoInteractions(userProfileRepository, userWorkspaceAccessRepository);
    }
}
