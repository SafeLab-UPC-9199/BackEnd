package com.test.backend.telemetry.domain.services;

/**
 * Canonical telemetry ingestion pipeline (Iteration 1 adapter extraction).
 *
 * Both transports — the MQTT subscriber and the HTTP ingestion endpoint —
 * normalize their input into (unit, payload) and delegate here, so sensor
 * resolution, metric-type validation, reading persistence, real-time metric
 * updates and threshold evaluation behave identically regardless of transport.
 */
public interface TelemetryIngestionService {

    /**
     * Ingests a telemetry payload for the given sensor unit.
     *
     * @param unit    sensor unit identifier (from MQTT topic or HTTP body)
     * @param payload JSON payload: {@code {"timestamp":<epochMillis>,"metrics":{...}}}
     * @throws IllegalArgumentException when the sensor unit is not registered
     * @throws IllegalStateException    when the payload cannot be parsed
     */
    void ingest(String unit, String payload);
}
