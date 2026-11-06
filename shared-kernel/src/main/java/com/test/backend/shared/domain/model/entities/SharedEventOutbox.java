package com.test.backend.shared.domain.model.entities;

import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "event_outbox", catalog = "safelab_shared")
public class SharedEventOutbox extends AuditableModel {

    public static final String DEFAULT_SCHEMA_VERSION = "1.0";

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Lob
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "schema_version", length = 16)
    private String schemaVersion;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public SharedEventOutbox(String eventType, String aggregateType, String aggregateId, String payload, String organizationId) {
        this(eventType, aggregateType, aggregateId, payload, organizationId, null, null);
    }

    public SharedEventOutbox(String eventType, String aggregateType, String aggregateId, String payload, String organizationId,
                       String correlationId, String schemaVersion) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.organizationId = organizationId;
        this.correlationId = correlationId != null ? correlationId : this.eventId;
        this.schemaVersion = schemaVersion != null ? schemaVersion : DEFAULT_SCHEMA_VERSION;
        this.retryCount = 0;
        this.status = "PENDING";
        this.occurredAt = LocalDateTime.now();
    }
}
