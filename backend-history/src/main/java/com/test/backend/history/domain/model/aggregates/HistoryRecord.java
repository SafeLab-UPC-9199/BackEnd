package com.test.backend.history.domain.model.aggregates;

import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
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
@Table(name = "history_records", catalog = "safelab_history")
public class HistoryRecord extends AuditableAbstractAggregateRoot<HistoryRecord> {

    @Column(name = "laboratory_id", nullable = true)
    private Long laboratoryId;

    private String name;
    private String description;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "event_type")
    private String eventType;

    private String severity;
    private String status;

    public HistoryRecord(com.test.backend.history.domain.model.commands.CreateHistoryRecordCommand command, String labName) {
        this.laboratoryId = labName != null ? Long.valueOf(labName) : null;
        this.name = command.name();
        this.description = command.description();
        this.occurredAt = command.occurredAt();
        this.eventType = command.eventType();
        this.severity = command.severity();
        this.status = command.status();
    }

    public HistoryRecord updateFrom(com.test.backend.history.domain.model.commands.UpdateHistoryRecordCommand command, String labName) {
        this.laboratoryId = labName != null ? Long.valueOf(labName) : null;
        this.name = command.name();
        this.description = command.description();
        this.occurredAt = command.occurredAt();
        this.eventType = command.eventType();
        this.severity = command.severity();
        this.status = command.status();
        return this;
    }
}
