package com.test.backend.labs.domain.model.aggregates;

import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workspaces", catalog = "safelab_labs")
public class Workspace extends AuditableAbstractAggregateRoot<Workspace> {

    /**
     * Code of the workspace seeded at startup (mirrors the monolith's
     * TemporarySeeder). Used as the fallback tenant for callers whose token
     * carries no workspaceId claim (ADR-006).
     */
    public static final String BASE_CODE = "base-operator-workspace";
    public static final String BASE_NAME = "SafeLab Base Workspace";

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    public Workspace(String name) {
        this.name = name;
        this.code = UUID.randomUUID().toString();
    }
}
