package com.test.backend.telemetry.application.internal.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.domain.model.events.SensorConfigurationDTO;
import com.test.backend.shared.domain.model.events.TemperatureReadingRecorded;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
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
 *   2. Record a {@link TemperatureReadingRecorded} domain event for downstream
 *      contexts (alerts, labs, automation, history). The event is written to the
 *      shared outbox (ADR-002) in the same transaction as the reading and is
 *      published to RabbitMQ by the dispatcher (ADR-005), so telemetry never
 *      calls alerts directly. This service does NOT create alerts, history
 *      records, or lab metrics directly — those are owned by their services.
 */
@Service
@Transactional
public class TelemetryIngestionServiceImpl implements TelemetryIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryIngestionServiceImpl.class);

    private final SensorConfigurationClient sensorConfigurationClient;
    private final SensorReadingRepository sensorReadingRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SharedEventOutboxRepository sharedEventOutboxRepository;
    private final ObjectMapper objectMapper = createObjectMapper();

    public TelemetryIngestionServiceImpl(SensorConfigurationClient sensorConfigurationClient,
                                         SensorReadingRepository sensorReadingRepository,
                                         MetricTypeRepository metricTypeRepository,
                                         ApplicationEventPublisher applicationEventPublisher,
                                         SharedEventOutboxRepository sharedEventOutboxRepository) {
        this.sensorConfigurationClient = sensorConfigurationClient;
        this.sensorReadingRepository = sensorReadingRepository;
        this.metricTypeRepository = metricTypeRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.sharedEventOutboxRepository = sharedEventOutboxRepository;
    }

    /**
     * Jackson mapper able to round-trip {@code LocalDateTime} (jsr310) as
     * ISO-8601 text: the outbox payload is serialized here and deserialized by
     * alerts, so the format must not depend on JVM defaults (array timestamps).
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

    private void processTelemetryData(SensorConfigurationDTO sensor, String payload) {
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

            // Domain event for decoupling (Iteration 1): written to the shared
            // outbox in this same transaction (ADR-002), then published to
            // RabbitMQ by SharedEventOutboxDispatcher (ADR-005). No direct call
            // to alerts, and no message can be lost between the commit and the
            // broker — a broker outage simply leaves the row PENDING.
            persistReadingRecorded(reading, sensor, metricType, val, recordedAt);
        }
    }

    /**
     * Serializes {@link TemperatureReadingRecorded} and appends it to the shared
     * outbox. Runs inside the ingestion transaction, so the reading and its event
     * commit (or roll back) together — the transactional-outbox guarantee of
     * ADR-002. Thresholds and lab name stay null, exactly as before: alerts falls
     * back to its default thermal limits when they are absent.
     */
    private void persistReadingRecorded(SensorReading reading, SensorConfigurationDTO sensor,
                                        MetricType metricType, Double value, LocalDateTime recordedAt) {
        try {
            var event = new TemperatureReadingRecorded(
                    reading.getId(),
                    sensor.laboratoryId(),
                    metricType.getId(),
                    metricType.getKey(),
                    value,
                    recordedAt,
                    null,  // minThreshold - resolved by alerts service
                    null,  // maxThreshold - resolved by alerts service
                    null   // labName - resolved by alerts service
            );
            var payload = objectMapper.writeValueAsString(event);
            sharedEventOutboxRepository.save(new SharedEventOutbox(
                    MqTopology.TEMPERATURE_READING_EVENT_TYPE,
                    "SensorReading",
                    String.valueOf(reading.getId()),
                    payload,
                    null,
                    null,
                    SharedEventOutbox.DEFAULT_SCHEMA_VERSION));
        } catch (JsonProcessingException ex) {
            // A reading that cannot be serialized must never break ingestion: the
            // measurement is already stored, only its downstream evaluation is lost.
            logger.warn("Failed to serialize TemperatureReadingRecorded for reading {}: {}",
                    reading.getId(), ex.getMessage());
        }
    }

    /**
     * Fallback invoked when the dependent service (automation) is unreachable or
     * the circuit breaker is open (ADR-004): telemetry ingestion continues with
     * reduced functionality instead of failing the request.
     *
     * <p>Client errors (unregistered sensor) are re-thrown so the REST layer can
     * answer 404 as documented — they are configured as {@code ignoreExceptions}
     * of the circuit breaker, so a wave of unknown units never opens the circuit.
     */
    public void ingestFallback(String unit, String payload, Throwable throwable) {
        if (throwable instanceof IllegalArgumentException clientError) {
            throw clientError;
        }
        logger.warn("Circuit breaker fallback for ingest(unit={}): {}", unit, throwable.getMessage());
        // Telemetry ingestion continues; downstream alert evaluation will be handled
        // when the circuit closes again.
    }
}
