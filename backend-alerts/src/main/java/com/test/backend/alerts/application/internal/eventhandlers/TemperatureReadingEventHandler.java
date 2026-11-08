package com.test.backend.alerts.application.internal.eventhandlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.test.backend.alerts.domain.model.entities.ProcessedEvent;
import com.test.backend.alerts.domain.services.EvaluateReadingUseCase;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.ProcessedEventRepository;
import com.test.backend.shared.domain.model.events.TemperatureReadingRecorded;
import com.test.backend.shared.infrastructure.messaging.MqTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Consumes {@code TemperatureReadingRecorded} from RabbitMQ (ADR-005) and runs
 * the thermal excursion evaluation, so telemetry and alerts stay decoupled as
 * separate bounded contexts (Iteration 1 / ADR-003).
 *
 * <p>Contract of this listener:
 * <ul>
 *   <li><b>Idempotent:</b> every delivery is checked against the consumer inbox
 *       ({@code processed_events}) before doing any work.</li>
 *   <li><b>Transactional:</b> the inbox row and the incident (plus its own outbox
 *       row) commit together, so a failure never leaves a half-processed message
 *       recorded as processed.</li>
 *   <li><b>Fail-fast to the DLQ:</b> exceptions propagate instead of being
 *       swallowed. The listener container retries a few times and then rejects
 *       the message without requeue, which the queue dead-letters to
 *       {@code alerts.temperature.reading.recorded.dlq} for inspection/replay.</li>
 * </ul>
 */
@Component
public class TemperatureReadingEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureReadingEventHandler.class);

    /** Only this metric can produce a thermal excursion incident. */
    private static final String THERMAL_METRIC_KEY = "temperature";

    private final EvaluateReadingUseCase evaluateReadingUseCase;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper = createObjectMapper();

    public TemperatureReadingEventHandler(EvaluateReadingUseCase evaluateReadingUseCase,
                                          ProcessedEventRepository processedEventRepository) {
        this.evaluateReadingUseCase = evaluateReadingUseCase;
        this.processedEventRepository = processedEventRepository;
    }

    /** Mirrors the producer's mapper: jsr310 types as ISO-8601 text. */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @RabbitListener(queues = MqTopology.TEMPERATURE_READING_QUEUE)
    @Transactional
    public void onMessage(Message message) {
        var event = deserialize(message);
        var idempotencyKey = idempotencyKey(message, event);

        if (processedEventRepository.existsByEventId(idempotencyKey)) {
            logger.info("Skipping duplicate delivery of {}", idempotencyKey);
            return;
        }

        // Inbox row first: the unique index on event_id is the concurrency guard
        // for two simultaneous deliveries of the same message. If the index wins
        // the race the insert fails, this transaction rolls back and the container
        // retries — at which point existsByEventId short-circuits the duplicate.
        processedEventRepository.saveAndFlush(new ProcessedEvent(
                idempotencyKey,
                MqTopology.TEMPERATURE_READING_EVENT_TYPE,
                event.readingId() != null ? String.valueOf(event.readingId()) : null,
                LocalDateTime.now(ZoneOffset.UTC)));

        evaluate(event);
    }

    /**
     * Evaluation is deliberately <b>not</b> wrapped in a try/catch (unlike the
     * original in-process listener): a failure here must reach the container so
     * the message is retried and finally dead-lettered instead of being lost with
     * a WARN line.
     */
    private void evaluate(TemperatureReadingRecorded event) {
        if (event.metricKey() == null || !THERMAL_METRIC_KEY.equalsIgnoreCase(event.metricKey())) {
            // Non-thermal readings are recorded as processed but never evaluated.
            return;
        }
        evaluateReadingUseCase.evaluate(
                event.laboratoryId(),
                event.value(),
                event.minThreshold(),
                event.maxThreshold(),
                event.labName()
        );
    }

    private TemperatureReadingRecorded deserialize(Message message) {
        var body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(body, TemperatureReadingRecorded.class);
        } catch (JsonProcessingException ex) {
            // Nothing can fix a broken payload: after the listener retries it is
            // dead-lettered, where an operator can inspect and discard it.
            throw new IllegalStateException(
                    "Malformed " + MqTopology.TEMPERATURE_READING_EVENT_TYPE + " payload: " + ex.getMessage(), ex);
        }
    }

    /**
     * Deduplication key: the AMQP messageId (the outbox eventId) when the message
     * came from telemetry; otherwise the readingId for manual replays published
     * straight to the exchange. Messages carrying neither cannot be deduplicated
     * and get a unique key so they are processed instead of silently skipped.
     */
    private String idempotencyKey(Message message, TemperatureReadingRecorded event) {
        var messageId = message.getMessageProperties().getMessageId();
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        if (event.readingId() != null) {
            return "reading-" + event.readingId();
        }
        return "unkeyed-" + UUID.randomUUID();
    }
}
