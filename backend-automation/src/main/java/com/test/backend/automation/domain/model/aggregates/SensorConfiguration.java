package com.test.backend.automation.domain.model.aggregates;

import com.test.backend.automation.domain.model.entities.CalibrationRecord;
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
@Table(name = "sensor_configurations", catalog = "safelab_automation")
public class SensorConfiguration extends AuditableAbstractAggregateRoot<SensorConfiguration> {

    @Column(name = "sensor_name", nullable = false)
    private String sensorName;

    private String type;

    private String unit;

    @Column(name = "calibration_date")
    private java.time.LocalDate calibrationDate;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "status")
    private String status = "INACTIVE";

    @Column(name = "last_connected")
    private java.time.LocalDateTime lastConnected;

    @Column(name = "laboratory_id")
    private Long laboratoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_threshold_id", nullable = true)
    private EquipmentThreshold equipment;

    @Column(name = "min_threshold")
    private Double minThreshold;

    @Column(name = "max_threshold")
    private Double maxThreshold;

    @Column(name = "warning_threshold")
    private Double warningThreshold;

    /**
     * Per-device HMAC secret (ADD Iteration: firma por dispositivo). When set, the HTTP
     * ingestion endpoint requires {@code X-Signature = hex(HMAC-SHA256(rawBody, deviceSecret))}
     * for frames carrying this unit - the shared deployment secret no longer satisfies it.
     * Never exposed through REST resources; provisioned only via
     * {@code POST /api/v1/sensor-configurations/{id}/device-secret} (Administrator).
     */
    @Column(name = "device_secret", length = 128)
    private String deviceSecret;

    @OneToMany(mappedBy = "sensorConfiguration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CalibrationRecord> calibrationRecords = new ArrayList<>();

    public SensorConfiguration(com.test.backend.automation.domain.model.commands.CreateSensorConfigurationCommand command, Long laboratoryId, EquipmentThreshold equipment) {
        this.sensorName = command.sensorName();
        this.type = command.type();
        this.unit = command.unit();
        this.isActive = command.isActive();
        this.laboratoryId = laboratoryId;
        this.equipment = equipment;
        this.minThreshold = command.minThreshold();
        this.maxThreshold = command.maxThreshold();
        this.warningThreshold = command.warningThreshold();
        this.status = "INACTIVE";
        this.lastConnected = null;
        this.calibrationDate = null;
    }

    public SensorConfiguration updateFrom(com.test.backend.automation.domain.model.commands.UpdateSensorConfigurationCommand command, Long laboratoryId, EquipmentThreshold equipment) {
        this.sensorName = command.sensorName();
        this.type = command.type();
        this.unit = command.unit();
        this.isActive = command.isActive();
        this.laboratoryId = laboratoryId;
        this.equipment = equipment;
        this.minThreshold = command.minThreshold();
        this.maxThreshold = command.maxThreshold();
        this.warningThreshold = command.warningThreshold();
        return this;
    }

    public SensorConfiguration calibrate(com.test.backend.automation.domain.model.commands.CalibrateSensorCommand command) {
        CalibrationRecord record = new CalibrationRecord();
        record.setSensorConfiguration(this);
        record.setCertificateId(command.certificateId());
        record.setExpirationDate(command.expirationDate());
        record.setCalibratedAt(command.calibratedAt());
        this.calibrationRecords.add(record);
        this.calibrationDate = command.calibratedAt().toLocalDate();
        return this;
    }
}
