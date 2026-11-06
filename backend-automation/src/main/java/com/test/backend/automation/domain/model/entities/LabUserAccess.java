package com.test.backend.automation.domain.model.entities;

import com.test.backend.automation.domain.model.aggregates.UserProfile;
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
@Table(name = "lab_user_accesses", catalog = "safelab_automation")
public class LabUserAccess extends AuditableModel {

    @Column(name = "user_profile_id", nullable = false)
    private Long userProfileId;

    @Column(name = "laboratory_id", nullable = false)
    private Long laboratoryId;

    @Column(name = "role_name", nullable = false)
    private String roleName;
}
