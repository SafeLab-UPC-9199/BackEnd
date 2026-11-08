package com.test.backend.alerts.domain.model.entities;

import com.test.backend.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Consumer inbox of the alerts RabbitMQ listener (ADR-005): one row per message
 * already handled.
 *
 * <p>The unique index on {@code event_id} is the idempotency guard. A message can
 * reach the listener more than once — a crash between the database commit and
 * the AMQP acknowledgement, a redelivery after a container restart, a replay
 * from the DLQ or a duplicate publish — and the second delivery stops here
 * instead of creating a second incident.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "processed_events", catalog = "safelab_alerts",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_events_event_id", columnNames = "event_id"))
public class ProcessedEvent extends AuditableModel {

    /** AMQP messageId (outbox eventId), or a synthetic key when it is absent. */
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** readingId of the aggregate the event refers to, for traceability. */
    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
