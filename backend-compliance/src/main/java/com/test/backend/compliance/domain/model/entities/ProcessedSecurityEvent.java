package com.test.backend.compliance.domain.model.entities;

import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "processed_events",
    catalog = "safelab_compliance",
    uniqueConstraints = @UniqueConstraint(name = "uk_processed_events_event_id", columnNames = "event_id")
)
public class ProcessedSecurityEvent extends AuditableModel {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
