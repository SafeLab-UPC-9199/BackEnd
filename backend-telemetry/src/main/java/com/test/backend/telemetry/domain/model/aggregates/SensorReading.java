package com.test.backend.telemetry.domain.model.aggregates;

import com.test.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.format.DateTimeFormatter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sensor_readings", catalog = "safelab_telemetry")
public class SensorReading extends AuditableAbstractAggregateRoot<SensorReading> {

    @Column(name = "sensor_configuration_id", nullable = false, length = 64)
    private String sensorConfigurationId;

    @Column(name = "laboratory_id", nullable = false)
    private Long laboratoryId;

    @ManyToOne
    @JoinColumn(name = "metric_type_id", nullable = false)
    private MetricType metricType;

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "recorded_at", nullable = false)
    private java.time.LocalDateTime recordedAt;

    // Compatibility getter: returns the metric key string from the catalog
    public String getMetricKey() {
        return metricType != null ? metricType.getKey() : null;
    }

    // Compatibility getter for legacy code that groups by date
    public String getDate() {
        if (this.recordedAt == null) return null;
        return this.recordedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    // Compatibility getter for legacy code that reads the metric value
    public Double getValue() {
        return this.metricValue;
    }

    public Long getLaboratoryId() {
        return laboratoryId;
    }

    public void setSensorConfigurationId(String sensorConfigurationId) {
        this.sensorConfigurationId = sensorConfigurationId;
    }

    public void setLaboratoryId(Long laboratoryId) {
        this.laboratoryId = laboratoryId;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = metricType;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public void setRecordedAt(java.time.LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
