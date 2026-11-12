package com.test.backend.shared.domain.model.events;

/**
 * Data transfer object for sensor configuration cross-service queries.
 * Used by Feign clients to query the automation service without coupling
 * to its domain model.
 */
public record SensorConfigurationDTO(
        String unit,
        String sensorName,
        Long laboratoryId,
        String equipmentName,
        Double minThreshold,
        Double maxThreshold,
        Double warningThreshold,
        String type,
        String calibrationDate,
        String status
) {
}
