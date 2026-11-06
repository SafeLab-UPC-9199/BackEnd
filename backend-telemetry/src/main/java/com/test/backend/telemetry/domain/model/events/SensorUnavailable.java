package com.test.backend.telemetry.domain.model.events;

import java.time.LocalDateTime;

/**
 * Published when a sensor reports OFFLINE (MQTT Last Will) so consumers can
 * react to sensor availability without polling sensor status.
 */
public record SensorUnavailable(
        String unit,
        String sensorName,
        LocalDateTime occurredAt
) {
}
