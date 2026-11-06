package com.test.backend.automation.interfaces.rest;

import com.test.backend.automation.domain.model.commands.CreateSensorConfigurationCommand;
import com.test.backend.automation.domain.model.commands.UpdateSensorConfigurationCommand;
import com.test.backend.automation.domain.model.commands.CalibrateSensorCommand;
import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.automation.interfaces.rest.resources.SensorConfigurationResource;
import com.test.backend.automation.interfaces.rest.resources.CalibrateSensorResource;
import com.test.backend.automation.domain.model.aggregates.EquipmentThreshold;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.EquipmentThresholdRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Tag(name = "Sensor Configurations", description = "Sensor Configurations management API")
@RestController
@RequestMapping("/api/v1/sensor-configurations")
public class SensorConfigurationController {

    private final SensorConfigurationRepository sensorConfigurationRepository;
    private final EquipmentThresholdRepository equipmentThresholdRepository;

    public SensorConfigurationController(SensorConfigurationRepository sensorConfigurationRepository,
                                         EquipmentThresholdRepository equipmentThresholdRepository) {
        this.sensorConfigurationRepository = sensorConfigurationRepository;
        this.equipmentThresholdRepository = equipmentThresholdRepository;
    }

    @GetMapping
    @Operation(summary = "Get all sensor configurations")
    public ResponseEntity<List<SensorConfigurationResource>> getAllConfigurations() {
        var configs = sensorConfigurationRepository.findAll();
        var resources = configs.stream()
                .map(c -> new SensorConfigurationResource(
                        c.getId(),
                        c.getSensorName(),
                        c.getType(),
                        c.getUnit(),
                        c.getCalibrationDate(),
                        c.isActive(),
                        c.getStatus(),
                        c.getLastConnected(),
                        c.getLaboratoryId(),
                        null,
                        c.getEquipment() != null ? c.getEquipment().getId() : null,
                        c.getEquipment() != null ? c.getEquipment().getName() : null,
                        c.getMinThreshold(),
                        c.getMaxThreshold(),
                        c.getWarningThreshold()
                ))
                .toList();
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{unit}")
    @Operation(summary = "Get sensor configuration by unit")
    public ResponseEntity<SensorConfigurationResource> getConfigurationByUnit(@PathVariable String unit) {
        return sensorConfigurationRepository.findByUnit(unit)
                .map(c -> ResponseEntity.ok(new SensorConfigurationResource(
                        c.getId(),
                        c.getSensorName(),
                        c.getType(),
                        c.getUnit(),
                        c.getCalibrationDate(),
                        c.isActive(),
                        c.getStatus(),
                        c.getLastConnected(),
                        c.getLaboratoryId(),
                        null,
                        c.getEquipment() != null ? c.getEquipment().getId() : null,
                        c.getEquipment() != null ? c.getEquipment().getName() : null,
                        c.getMinThreshold(),
                        c.getMaxThreshold(),
                        c.getWarningThreshold()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a new sensor configuration")
    public ResponseEntity<SensorConfigurationResource> createConfiguration(@RequestBody CreateSensorConfigurationCommand command) {
        var existing = sensorConfigurationRepository.findByUnit(command.unit());
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        var config = new SensorConfiguration();
        config.setUnit(command.unit());
        config.setSensorName(command.sensorName());
        config.setType(command.type());
        config.setMinThreshold(command.minThreshold());
        config.setMaxThreshold(command.maxThreshold());
        config.setWarningThreshold(command.warningThreshold());
        config.setStatus("ACTIVE");

        var saved = sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(new SensorConfigurationResource(
                saved.getId(),
                saved.getSensorName(),
                saved.getType(),
                saved.getUnit(),
                saved.getCalibrationDate(),
                config.isActive(),
                saved.getStatus(),
                saved.getLastConnected(),
                saved.getLaboratoryId(),
                null,
                saved.getEquipment() != null ? saved.getEquipment().getId() : null,
                saved.getEquipment() != null ? saved.getEquipment().getName() : null,
                saved.getMinThreshold(),
                saved.getMaxThreshold(),
                saved.getWarningThreshold()
        ));
    }

    @PutMapping("/{unit}")
    @Operation(summary = "Update an existing sensor configuration")
    public ResponseEntity<SensorConfigurationResource> updateConfiguration(@PathVariable String unit,
                                                                          @RequestBody UpdateSensorConfigurationCommand command) {
        var configOpt = sensorConfigurationRepository.findByUnit(unit);
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var config = configOpt.get();
        if (command.sensorName() != null) config.setSensorName(command.sensorName());
        if (command.type() != null) config.setType(command.type());
        if (command.minThreshold() != null) config.setMinThreshold(command.minThreshold());
        if (command.maxThreshold() != null) config.setMaxThreshold(command.maxThreshold());
        if (command.warningThreshold() != null) config.setWarningThreshold(command.warningThreshold());

        var saved = sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(new SensorConfigurationResource(
                saved.getId(),
                saved.getSensorName(),
                saved.getType(),
                saved.getUnit(),
                saved.getCalibrationDate(),
                config.isActive(),
                saved.getStatus(),
                saved.getLastConnected(),
                saved.getLaboratoryId(),
                null,
                saved.getEquipment() != null ? saved.getEquipment().getId() : null,
                saved.getEquipment() != null ? saved.getEquipment().getName() : null,
                saved.getMinThreshold(),
                saved.getMaxThreshold(),
                saved.getWarningThreshold()
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a sensor configuration")
    public ResponseEntity<Void> deleteConfiguration(@PathVariable Long id) {
        if (!sensorConfigurationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        sensorConfigurationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/calibrate")
    @Operation(summary = "Calibrate a sensor")
    public ResponseEntity<Map<String, Object>> calibrateSensor(@RequestBody CalibrateSensorCommand command) {
        var configOpt = sensorConfigurationRepository.findById(command.sensorConfigurationId());
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var config = configOpt.get();
        config.setCalibrationDate(command.calibratedAt().toLocalDate());
        sensorConfigurationRepository.save(config);

        return ResponseEntity.ok(Map.of(
                "status", "CALIBRATED",
                "sensorConfigurationId", command.sensorConfigurationId(),
                "calibrationDate", command.calibratedAt()
        ));
    }
}
