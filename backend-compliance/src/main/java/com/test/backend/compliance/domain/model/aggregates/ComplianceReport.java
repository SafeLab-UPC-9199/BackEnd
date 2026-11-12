package com.test.backend.compliance.domain.model.aggregates;

import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "compliance_reports", catalog = "safelab_compliance")
public class ComplianceReport extends AuditableAbstractAggregateRoot<ComplianceReport> {

    @Column(name = "laboratory_id", nullable = true)
    private Long laboratoryId;

    private String name;
    private String type;
    private String period;
    private String size;
    private String status;

    @Column(name = "period_start")
    private java.time.LocalDateTime periodStart;

    @Column(name = "period_end")
    private java.time.LocalDateTime periodEnd;

    @Column(name = "digital_signature")
    private String digitalSignature;

    @Column(name = "generated_at")
    private java.time.LocalDateTime generatedAt;

    @ManyToMany
    @JoinTable(
            catalog = "safelab_compliance",
            name = "report_audit_links",
            joinColumns = @JoinColumn(name = "report_id"),
            inverseJoinColumns = @JoinColumn(name = "audit_id")
            )
    private List<AuditTrail> auditTrails = new ArrayList<>();
}
