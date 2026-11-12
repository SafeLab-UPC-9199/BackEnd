package com.test.backend.alerts.domain.model.aggregates;

import com.test.backend.alerts.domain.model.entities.AlertMetric;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "alerts", catalog = "safelab_alerts")
public class Alert extends AuditableAbstractAggregateRoot<Alert> {
    @ManyToOne
    @JoinColumn(name = "laboratory_id", nullable = true)
    private Laboratory laboratory;
    @ManyToOne
    @JoinColumn(name = "sensor_configuration_id", nullable = true)
    private SensorConfiguration sensorConfiguration;

    private String title;

    private String description;

    private String severity;

    private String status;

    @Column(name = "lab_name")
    private String labName;

    @Column(name = "time_ago")
    private String timeAgo;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertMetric> metrics = new ArrayList<>();

    public Alert(com.test.backend.alerts.domain.model.commands.CreateAlertCommand command, Laboratory laboratory) {
        this.laboratory = laboratory;
        this.title = command.title();
        this.description = command.description();
        this.severity = command.severity();
        this.status = command.status();
        this.labName = command.labName();
        this.timeAgo = command.timeAgo();
    }

    public Alert updateFrom(com.test.backend.alerts.domain.model.commands.UpdateAlertCommand command, Laboratory laboratory) {
        this.laboratory = laboratory;
        this.title = command.title();
        this.description = command.description();
        this.severity = command.severity();
        this.status = command.status();
        this.labName = command.labName();
        this.timeAgo = command.timeAgo();
        return this;
    }

    public void acknowledge() {
        if ("RESOLVED".equalsIgnoreCase(this.status) || "CLOSED".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Cannot acknowledge an already resolved or closed alert");
        }
        this.status = "ACKNOWLEDGED";
    }

    public void attend() {
        if ("RESOLVED".equalsIgnoreCase(this.status) || "CLOSED".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Cannot attend an already resolved or closed alert");
        }
        this.status = "IN_PROGRESS";
    }

    public void resolve() {
        if ("CLOSED".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Alert is already closed");
        }
        this.status = "RESOLVED";
    }

    public void close() {
        if (!"RESOLVED".equalsIgnoreCase(this.status)) {
            throw new IllegalStateException("Cannot close an un-resolved alert. Resolve first.");
        }
        this.status = "CLOSED";
    }
}
