package com.test.backend.iam.application.internal.queryservices;

import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import com.test.backend.iam.domain.model.aggregates.User;
import com.test.backend.iam.domain.model.queries.GetAllUsersQuery;
import com.test.backend.iam.domain.model.queries.GetUserByEmailQuery;
import com.test.backend.iam.domain.model.queries.GetUserByIdQuery;
import com.test.backend.iam.domain.services.UserQueryService;
import com.test.backend.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserWorkspaceAccessRepository userWorkspaceAccessRepository;
    private final CurrentWorkspaceService currentWorkspaceService;

    public UserQueryServiceImpl(UserRepository userRepository,
                                UserProfileRepository userProfileRepository,
                                UserWorkspaceAccessRepository userWorkspaceAccessRepository,
                                CurrentWorkspaceService currentWorkspaceService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userWorkspaceAccessRepository = userWorkspaceAccessRepository;
        this.currentWorkspaceService = currentWorkspaceService;
    }

    @Override
    public List<User> handle(GetAllUsersQuery query) {
        // QAS-05: the user directory is scoped to the caller's workspace (via email ->
        // UserProfile/UserWorkspaceAccess, the only workspace link the iam User has).
        // Administrators see every registered user.
        if (currentWorkspaceService.isCurrentUserAdmin()) {
            return userRepository.findAll();
        }
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            // Fail closed: a non-admin without a workspace sees nobody.
            return List.of();
        }
        Set<String> memberEmails = workspaceMemberEmails(workspaceIdOpt.get());
        if (memberEmails.isEmpty()) {
            return List.of();
        }
        return userRepository.findByEmailIn(memberEmails);
    }

    @Override
    public Optional<User> handle(GetUserByIdQuery query) {
        var userOpt = userRepository.findById(query.userId());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        if (currentWorkspaceService.isCurrentUserAdmin()) {
            return userOpt;
        }
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            // Fail closed: a non-admin without a workspace cannot read user records.
            return Optional.empty();
        }
        return isWorkspaceMember(userOpt.get().getEmail(), workspaceIdOpt.get()) ? userOpt : Optional.empty();
    }

    @Override
    public Optional<User> handle(GetUserByEmailQuery query) {
        return userRepository.findByEmail(query.email());
    }

    /** Emails of every profile that belongs to the workspace (primary membership + granted access). */
    private Set<String> workspaceMemberEmails(Long workspaceId) {
        Set<String> emails = new LinkedHashSet<>();
        userProfileRepository.findByWorkspaceId(workspaceId)
                .forEach(profile -> emails.add(profile.getEmail()));
        userWorkspaceAccessRepository.findByWorkspaceId(workspaceId)
                .forEach(access -> {
                    if (access.getUserProfile() != null && access.getUserProfile().getEmail() != null) {
                        emails.add(access.getUserProfile().getEmail());
                    }
                });
        return emails;
    }

    private boolean isWorkspaceMember(String email, Long workspaceId) {
        if (email == null) {
            return false;
        }
        boolean primary = userProfileRepository.findByEmail(email)
                .map(profile -> workspaceId.equals(profile.getWorkspaceId()))
                .orElse(false);
        if (primary) {
            return true;
        }
        return userWorkspaceAccessRepository.findByUserProfileEmailAndWorkspaceId(email, workspaceId).isPresent();
    }
}
