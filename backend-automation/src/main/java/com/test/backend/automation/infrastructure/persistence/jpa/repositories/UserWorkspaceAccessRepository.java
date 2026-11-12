package com.test.backend.automation.infrastructure.persistence.jpa.repositories;

import com.test.backend.automation.domain.model.entities.UserWorkspaceAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserWorkspaceAccessRepository extends JpaRepository<UserWorkspaceAccess, Long> {
    List<UserWorkspaceAccess> findByUserProfileId(Long userProfileId);
    List<UserWorkspaceAccess> findByWorkspaceId(Long workspaceId);
    Optional<UserWorkspaceAccess> findByUserProfileIdAndWorkspaceId(Long userProfileId, Long workspaceId);
    // findByUserProfileEmailAndWorkspaceId removed: UserWorkspaceAccess was
    // denormalized to a plain userProfileId, so Spring Data could not resolve
    // the nested "userProfileEmail" property ("No property 'userProfileEmail'
    // found"). Callers resolve the profile id first (as the module code does).
}
