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
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
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

@Tag(name = "Sensor Configurations", description = "Sensor Configurations management API")
@RestController
@RequestMapping("/api/v1/sensor-configurations")
public class SensorConfigurationController {

    private final SensorConfigurationRepository sensorConfigurationRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EquipmentThresholdRepository equipmentThresholdRepository;
    private final CurrentWorkspaceService currentWorkspaceService;

    public SensorConfigurationController(SensorConfigurationRepository sensorConfigurationRepository,
                                         LaboratoryRepository laboratoryRepository,
                                         EquipmentThresholdRepository equipmentThresholdRepository,
                                         CurrentWorkspaceService currentWorkspaceService) {
        this.sensorConfigurationRepository = sensorConfigurationRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.equipmentThresholdRepository = equipmentThresholdRepository;
        this.currentWorkspaceService = currentWorkspaceService;
    }

    @GetMapping
    @Operation(summary = "Get all sensor configurations")
    public ResponseEntity<List<SensorConfigurationResource>> getAllConfigurations() {
        var profileOpt = currentWorkspaceService.getCurrentUserProfile();
        if (profileOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        var profile = profileOpt.get();
        var configs = sensorConfigurationRepository.findByLaboratoryWorkspaceId(profile.getWorkspaceId());

        if (profile.getRole() != null && "Administrator".equalsIgnoreCase(profile.getRole().getName())) {
            var resources = configs.stream()
                    .map(this::toResource)
                    .toList();
            return ResponseEntity.ok(resources);
        }

        var allowedLabIds = profile.getLabAccesses().stream()
                .map(access -> access.getLaboratory().getId())
                .toList();

        var resources = configs.stream()
                .filter(c -> c.getLaboratory() != null && allowedLabIds.contains(c.getLaboratory().getId()))
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    @PostMapping
    @Operation(summary = "Create a new sensor configuration")
    public ResponseEntity<SensorConfigurationResource> createConfiguration(@RequestBody SensorConfigurationResource resource) {
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Laboratory laboratory = null;
        if (resource.laboratoryId() != null) {
            laboratory = laboratoryRepository.findByIdAndWorkspaceId(resource.laboratoryId(), workspaceIdOpt.get()).orElse(null);
            if (laboratory == null) {
                return ResponseEntity.badRequest().build(); // Laboratory does not exist in user's workspace
            }
        }

        EquipmentThreshold equipment = null;
        if (resource.equipmentId() != null) {
            // QAS-05: equipment comes from the request body, so it must belong to the
            // caller's workspace before being attached to the configuration.
            equipment = equipmentThresholdRepository
                    .findByIdAndLaboratoryWorkspaceId(resource.equipmentId(), workspaceIdOpt.get())
                    .orElse(null);
            if (equipment == null) {
                return ResponseEntity.badRequest().build(); // Equipment does not exist in user's workspace
            }
        }

        var command = new CreateSensorConfigurationCommand(
                resource.sensorName(),
                resource.type(),
                resource.unit(),
                resource.isActive(),
                resource.laboratoryId(),
                resource.equipmentId(),
                resource.minThreshold(),
                resource.maxThreshold(),
                resource.warningThreshold()
        );
        var config = new SensorConfiguration(command, laboratory, equipment);
        sensorConfigurationRepository.save(config);
        return new ResponseEntity<>(toResource(config), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing sensor configuration")
    public ResponseEntity<SensorConfigurationResource> updateConfiguration(@PathVariable Long id, @RequestBody SensorConfigurationResource resource) {
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = sensorConfigurationRepository.findByIdAndLaboratoryWorkspaceId(id, workspaceIdOpt.get());
        if (result.isEmpty()) return ResponseEntity.notFound().build();

        var config = result.get();
        Laboratory laboratory = config.getLaboratory();
        if (resource.laboratoryId() != null) {
            laboratory = laboratoryRepository.findByIdAndWorkspaceId(resource.laboratoryId(), workspaceIdOpt.get()).orElse(null);
            if (laboratory == null) {
                return ResponseEntity.badRequest().build();
            }
        }

        EquipmentThreshold equipment = null;
        if (resource.equipmentId() != null) {
            // QAS-05: same rule on update - foreign equipment is rejected, not attached.
            equipment = equipmentThresholdRepository
                    .findByIdAndLaboratoryWorkspaceId(resource.equipmentId(), workspaceIdOpt.get())
                    .orElse(null);
            if (equipment == null) {
                return ResponseEntity.badRequest().build(); // Equipment does not exist in user's workspace
            }
        }

        var command = new UpdateSensorConfigurationCommand(
                id,
                resource.sensorName() != null ? resource.sensorName() : config.getSensorName(),
                resource.type() != null ? resource.type() : config.getType(),
                resource.unit() != null ? resource.unit() : config.getUnit(),
                resource.isActive(),
                resource.laboratoryId() != null ? resource.laboratoryId() : (config.getLaboratory() != null ? config.getLaboratory().getId() : null),
                resource.equipmentId(),
                resource.minThreshold(),
                resource.maxThreshold(),
                resource.warningThreshold()
        );
        config.updateFrom(command, laboratory, equipment);
        sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(toResource(config));
    }

    @PostMapping("/{id}/calibrate")
    @Operation(summary = "Calibrate a sensor")
    public ResponseEntity<SensorConfigurationResource> calibrateSensor(@PathVariable Long id, @RequestBody CalibrateSensorResource resource) {
        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var result = sensorConfigurationRepository.findByIdAndLaboratoryWorkspaceId(id, workspaceIdOpt.get());
        if (result.isEmpty()) return ResponseEntity.notFound().build();

        var config = result.get();
        var command = new CalibrateSensorCommand(
                id,
                resource.certificateId(),
                resource.expirationDate(),
                resource.calibratedAt()
        );
        config.calibrate(command);
        sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(toResource(config));
    }

    /**
     * Fase firma por dispositivo: provisions a fresh per-device HMAC secret for the sensor.
     * Administrator-only (credential management), returned exactly once so the operator can
     * load it onto the edge device; it is never readable through the regular resources.
     */
    @PostMapping("/{id}/device-secret")
    @Operation(summary = "Provision (or rotate) the per-device HMAC secret used to sign HTTP ingestion frames")
    public ResponseEntity<Map<String, String>> provisionDeviceSecret(@PathVariable Long id) {
        if (!currentWorkspaceService.isCurrentUserAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "ERROR",
                    "message", "Only administrators can manage device credentials."
            ));
        }
        var configOpt = sensorConfigurationRepository.findById(id);
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var config = configOpt.get();
        String secret = generateDeviceSecret();
        config.setDeviceSecret(secret);
        sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(Map.of(
                "sensorId", String.valueOf(config.getId()),
                "unit", config.getUnit() != null ? config.getUnit() : "",
                "deviceSecret", secret
        ));
    }

    /**
     * Removes the per-device secret so the sensor falls back to the shared deployment
     * signature (or none, when no secret is configured). Administrator-only.
     */
    @DeleteMapping("/{id}/device-secret")
    @Operation(summary = "Revoke the per-device HMAC secret (falls back to the shared secret)")
    public ResponseEntity<Map<String, String>> revokeDeviceSecret(@PathVariable Long id) {
        if (!currentWorkspaceService.isCurrentUserAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "ERROR",
                    "message", "Only administrators can manage device credentials."
            ));
        }
        var configOpt = sensorConfigurationRepository.findById(id);
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var config = configOpt.get();
        config.setDeviceSecret(null);
        sensorConfigurationRepository.save(config);
        return ResponseEntity.ok(Map.of("status", "REVOKED", "sensorId", String.valueOf(config.getId())));
    }

    /** 32 random bytes rendered as hex - 256 bits of entropy for HmacSHA256. */
    private String generateDeviceSecret() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    private SensorConfigurationResource toResource(SensorConfiguration config) {
        return new SensorConfigurationResource(
                config.getId(),
                config.getSensorName(),
                config.getType(),
                config.getUnit(),
                config.getCalibrationDate(),
                config.isActive(),
                config.getStatus(),
                config.getLastConnected(),
                config.getLaboratory() != null ? config.getLaboratory().getId() : null,
                config.getLaboratory() != null ? config.getLaboratory().getName() : null,
                config.getEquipment() != null ? config.getEquipment().getId() : null,
                config.getEquipment() != null ? config.getEquipment().getName() : null,
                config.getMinThreshold(),
                config.getMaxThreshold(),
                config.getWarningThreshold()
        );
    }
}
