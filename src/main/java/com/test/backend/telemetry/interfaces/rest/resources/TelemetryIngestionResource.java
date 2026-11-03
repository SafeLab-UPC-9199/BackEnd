package com.test.backend.telemetry.interfaces.rest.resources;

import java.util.Map;

/**
 * HTTP telemetry ingestion request. Mirrors the MQTT payload shape so both
 * transports feed the same canonical pipeline.
 *
 * Example: {@code {"unit":"Lab-04","timestamp":1712345678900,"metrics":{"temperature":5.4}}}
 */
public record TelemetryIngestionResource(
        String unit,
        Long timestamp,
        Map<String, Object> metrics
) {}
