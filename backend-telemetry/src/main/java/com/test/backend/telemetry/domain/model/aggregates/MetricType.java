package com.test.backend.telemetry.domain.model.aggregates;

import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.test.backend.telemetry.domain.model.commands.CreateMetricTypeCommand;
import com.test.backend.telemetry.domain.model.commands.UpdateMetricTypeCommand;
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
@Table(name = "metric_types", catalog = "safelab_telemetry")
public class MetricType extends AuditableAbstractAggregateRoot<MetricType> {

    @Column(name = "metric_key", unique = true, nullable = false)
    private String key;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private String icon;

    private String category;

    @Column(nullable = false)
    private boolean active = true;

    public MetricType(CreateMetricTypeCommand command) {
        this.key = command.key();
        this.displayName = command.displayName();
        this.unit = command.unit();
        this.icon = command.icon();
        this.category = command.category();
        this.active = true;
    }

    public MetricType updateFrom(UpdateMetricTypeCommand command) {
        this.key = command.key();
        this.displayName = command.displayName();
        this.unit = command.unit();
        this.icon = command.icon();
        this.category = command.category();
        this.active = command.active();
        return this;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    public String getIcon() {
        return icon;
    }

    public String getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
