package com.test.backend.automation.domain.model.entities;

import com.test.backend.automation.domain.model.aggregates.UserProfile;
import com.test.backend.automation.domain.model.entities.Role;
import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_workspace_accesses", catalog = "safelab_automation")
public class UserWorkspaceAccess extends AuditableModel {

    @Column(name = "user_profile_id", nullable = false)
    private Long userProfileId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;
}
