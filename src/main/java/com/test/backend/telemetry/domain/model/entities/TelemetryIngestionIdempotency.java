package com.test.backend.telemetry.domain.model.entities;

import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "telemetry_ingestion_idempotency", catalog = "safelab_telemetry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_telemetry_idem_key", columnNames = {"idempotency_key"})
})
public class TelemetryIngestionIdempotency extends AuditableModel {

    // id is inherited from AuditableModel (@Id + identity). Hibernate 7 forbids
    // the subclass from respecifying the generation strategy.

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 128)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Lob
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "unit", length = 100)
    private String unit;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // createdAt is inherited from AuditableModel (populated by JPA auditing).
}
