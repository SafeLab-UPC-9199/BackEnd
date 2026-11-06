package com.test.backend.telemetry.application.internal.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.backend.shared.domain.model.events.TemperatureReadingRecorded;
import com.test.backend.telemetry.domain.model.aggregates.MetricType;
import com.test.backend.telemetry.domain.model.aggregates.SensorReading;
import com.test.backend.telemetry.domain.model.events.ReadingRejected;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.infrastructure.clients.SensorConfigurationClient;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.MetricTypeRepository;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.SensorReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical telemetry ingestion pipeline (Iteration 1).
 * Responsibilities:
 *   1. Validate and persist sensor readings to the telemetry bounded context.
 *   2. Publish {@link TemperatureReadingRecorded} domain events for downstream
 *      contexts (alerts, labs, automation, history) to consume via their own
 *      event handlers. This service does NOT create alerts, history records,
 *      or lab metrics directly — those are owned by their respective services.
 */
@Service
@Transactional
public class TelemetryIngestionServiceImpl implements TelemetryIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryIngestionServiceImpl.class);

    private final SensorConfigurationClient sensorConfigurationClient;
    private final SensorReadingRepository sensorReadingRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelemetryIngestionServiceImpl(SensorConfigurationClient sensorConfigurationClient,
                                         SensorReadingRepository sensorReadingRepository,
                                         MetricTypeRepository metricTypeRepository,
                                         ApplicationEventPublisher applicationEventPublisher) {
        this.sensorConfigurationClient = sensorConfigurationClient;
        this.sensorReadingRepository = sensorReadingRepository;
        this.metricTypeRepository = metricTypeRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    @CircuitBreaker(name = "labs-service", fallbackMethod = "ingestFallback")
    @Retry(name = "labs-service")
    public void ingest(String unit, String payload) {
        // Validate sensor via automation service (Feign client)
        var sensorOpt = sensorConfigurationClient.findByUnit(unit);
        if (sensorOpt.isEmpty()) {
            logger.warn("Security warning: received telemetry for unregistered sensor unit '{}'", unit);
            publishRejection(unit, "Unregistered sensor unit");
            throw new IllegalArgumentException("Unregistered sensor unit: " + unit);
        }
        var sensor = sensorOpt.get();
        processTelemetryData(sensor, payload);
    }

    private void publishRejection(String unit, String reason) {
        try {
            applicationEventPublisher.publishEvent(new ReadingRejected(
                    unit, reason, LocalDateTime.now(java.time.ZoneOffset.UTC)));
        } catch (Exception ex) {
            logger.debug("Failed to publish ReadingRejected event: {}", ex.getMessage());
        }
    }

    private void processTelemetryData(com.test.backend.shared.domain.model.events.SensorConfigurationDTO sensor, String payload) {
        Map<String, Object> data;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            data = parsed;
        } catch (Exception e) {
            publishRejection(sensor.unit(), "Malformed JSON payload");
            throw new IllegalStateException("Invalid telemetry payload: " + e.getMessage(), e);
        }

        Long timestamp = null;
        if (data.containsKey("timestamp")) {
            Object tsObj = data.get("timestamp");
            if (tsObj instanceof Number) {
                timestamp = ((Number) tsObj).longValue();
            }
        }
        if (timestamp == null) {
            timestamp = System.currentTimeMillis();
        }

        LocalDateTime recordedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), java.time.ZoneOffset.UTC);

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");
        if (metrics == null || metrics.isEmpty()) {
            publishRejection(sensor.unit(), "Missing or empty metrics");
            return;
        }

        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            String key = entry.getKey();
            Double val = null;
            if (entry.getValue() instanceof Number) {
                val = ((Number) entry.getValue()).doubleValue();
            }
            if (val == null) continue;

            Optional<MetricType> metricTypeOpt = metricTypeRepository.findByKey(key);
            if (metricTypeOpt.isEmpty()) {
                logger.warn("Unknown metric key '{}' from sensor '{}' — ignoring.", key, sensor.unit());
                publishRejection(sensor.unit(), "Unknown metric key: " + key);
                continue;
            }
            MetricType metricType = metricTypeOpt.get();

            SensorReading reading = new SensorReading();
            reading.setSensorConfigurationId(sensor.unit());
            reading.setLaboratoryId(sensor.laboratoryId());
            reading.setMetricType(metricType);
            reading.setMetricValue(val);
            reading.setRecordedAt(recordedAt);
            sensorReadingRepository.save(reading);

            // Publish domain event for decoupling (Iteration 1)
            try {
                applicationEventPublisher.publishEvent(new TemperatureReadingRecorded(
                        reading.getId(),
                        sensor.laboratoryId(),
                        metricType.getId(),
                        metricType.getKey(),
                        val,
                        recordedAt,
                        null,  // minThreshold - resolved by alerts service
                        null,  // maxThreshold - resolved by alerts service
                        null   // labName - resolved by alerts service
                ));
            } catch (Exception ex) {
                logger.debug("Failed to publish TemperatureReadingRecorded event: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback invoked when the labs/alerts service is unavailable (circuit open).
     * Logs the failure but does not block telemetry ingestion.
     */
    public void ingestFallback(String unit, String payload, Throwable throwable) {
        logger.warn("Circuit breaker fallback for ingest(unit={}): {}", unit, throwable.getMessage());
        // Telemetry ingestion continues; downstream alert evaluation will be handled
        // when the circuit closes again.
    }
}
