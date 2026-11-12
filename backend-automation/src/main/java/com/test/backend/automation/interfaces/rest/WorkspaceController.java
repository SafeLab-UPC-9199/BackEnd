package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserProfileRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Workspaces", description = "Workspaces management API")
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final UserWorkspaceAccessRepository userWorkspaceAccessRepository;
    private final UserProfileRepository userProfileRepository;

    public WorkspaceController(UserWorkspaceAccessRepository userWorkspaceAccessRepository,
                               UserProfileRepository userProfileRepository) {
        this.userWorkspaceAccessRepository = userWorkspaceAccessRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public record WorkspaceResource(Long id, String name, String code, String roleName) {}

    @GetMapping("/my-workspaces")
    @Operation(summary = "Get all workspaces the current user has access to")
    public ResponseEntity<List<WorkspaceResource>> getMyWorkspaces() {
        var accesses = userWorkspaceAccessRepository.findAll();
        var resources = accesses.stream()
                .map(a -> new WorkspaceResource(
                        a.getId(),
                        "Workspace " + a.getId(),
                        "WS-" + a.getId(),
                        a.getRoleId() != null ? "Role-" + a.getRoleId() : "User"
                ))
                .toList();
        return ResponseEntity.ok(resources);
    }
}
