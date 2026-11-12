package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.UserProfile;
import com.test.backend.automation.domain.model.commands.UpdateUserProfileCommand;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.RoleRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import com.test.backend.automation.interfaces.rest.resources.UserProfileResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User Profiles", description = "User Profiles management API")
@RestController
@RequestMapping("/api/v1/user-profiles")
public class UserProfileController {

    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final UserWorkspaceAccessRepository userWorkspaceAccessRepository;

    public UserProfileController(UserProfileRepository userProfileRepository,
                                 RoleRepository roleRepository,
                                 UserWorkspaceAccessRepository userWorkspaceAccessRepository) {
        this.userProfileRepository = userProfileRepository;
        this.roleRepository = roleRepository;
        this.userWorkspaceAccessRepository = userWorkspaceAccessRepository;
    }

    @GetMapping
    @Operation(summary = "Get all user profiles")
    public ResponseEntity<List<UserProfileResource>> getAllProfiles() {
        var profiles = userProfileRepository.findAll();
        var resources = profiles.stream()
                .map(p -> new UserProfileResource(
                        p.getId(),
                        p.getFullName(),
                        p.getRole() != null ? p.getRole().getName() : "",
                        p.getEmail(),
                        p.getAvatarUrl(),
                        p.getPhoneNumber(),
                        p.getProfessionalTitle(),
                        p.getEmployeeId(),
                        p.getSystemState(),
                        p.getAccessTier(),
                        p.getDefaultStartShift(),
                        p.getShiftDuration(),
                        p.isAutoGenerateShiftReport(),
                        List.<Long>of()
                ))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user profile")
    public ResponseEntity<UserProfileResource> updateProfile(@PathVariable Long id,
                                                            @RequestBody UserProfileResource resource) {
        var result = userProfileRepository.findById(id);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var profile = result.get();
        profile.setFullName(resource.fullName());
        profile.setEmail(resource.email());
        userProfileRepository.save(profile);
        return ResponseEntity.ok(toResource(profile));
    }

    private UserProfileResource toResource(UserProfile profile) {
        return new UserProfileResource(
                profile.getId(),
                profile.getFullName(),
                profile.getRole() != null ? profile.getRole().getName() : "",
                profile.getEmail(),
                profile.getAvatarUrl(),
                profile.getPhoneNumber(),
                profile.getProfessionalTitle(),
                profile.getEmployeeId(),
                profile.getSystemState(),
                profile.getAccessTier(),
                profile.getDefaultStartShift(),
                profile.getShiftDuration(),
                profile.isAutoGenerateShiftReport(),
                List.<Long>of()
        );
    }
}
