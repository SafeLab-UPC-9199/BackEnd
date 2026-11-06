package com.test.backend.telemetry.infrastructure.mqtt;

import com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties;
import com.test.backend.telemetry.domain.model.events.SensorUnavailable;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.TelemetryIngestionIdempotencyRepository;
import com.test.backend.telemetry.domain.model.entities.TelemetryIngestionIdempotency;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * MQTT transport adapter: parses topic/payload and delegates the canonical
 * normalization pipeline to {@link TelemetryIngestionService}, which is shared
 * with the HTTP ingestion endpoint.
 */
@Service
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber implements MqttCallback {

    private static final Logger logger = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

    private final MqttClient client;
    private final MqttConnectionOptions connectOptions;
    private final MqttProperties mqttProperties;
    private final TransactionTemplate transactionTemplate;
    private final TelemetryIngestionService telemetryIngestionService;
    private final TelemetryIngestionIdempotencyRepository idempotencyRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final long idempotencyTtlMinutes;

    public MqttTelemetrySubscriber(MqttClient client,
                                   MqttConnectionOptions connectOptions,
                                   MqttProperties mqttProperties,
                                   PlatformTransactionManager transactionManager,
                                   TelemetryIngestionService telemetryIngestionService,
                                   TelemetryIngestionIdempotencyRepository idempotencyRepository,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   @org.springframework.beans.factory.annotation.Value("${telemetry.idempotency.ttl-minutes:60}") long idempotencyTtlMinutes) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.mqttProperties = mqttProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.telemetryIngestionService = telemetryIngestionService;
        this.idempotencyRepository = idempotencyRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.idempotencyTtlMinutes = idempotencyTtlMinutes;
    }

    @PostConstruct
    public void init() {
        client.setCallback(this);
        new Thread(this::connect).start();
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
            logger.info("MQTT Client disconnected successfully.");
        } catch (MqttException e) {
            logger.error("Error closing MQTT Client", e);
        }
    }

    private void connect() {
        try {
            if (client.isConnected()) return;
            logger.info("Attempting to connect to MQTT broker: {}", client.getServerURI());
            client.connect(connectOptions);
            logger.info("Successfully connected to MQTT broker.");
        } catch (MqttException e) {
            logger.error("Failed to connect to MQTT broker. Will retry with automatic reconnect.", e);
            try {
                Thread.sleep(2000);
                if (!client.isConnected()) {
                    client.connect(connectOptions);
                }
            } catch (Exception ex) {
                logger.warn("MQTT reconnect attempt failed: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            String topic = mqttProperties.getTopic() != null ? mqttProperties.getTopic() : "safelab/#";
            client.subscribe(topic, 1);
            logger.info("Subscribed to MQTT topic: {}", topic);
        } catch (MqttException e) {
            logger.error("Failed to subscribe to MQTT topic after connection", e);
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        logger.warn("MQTT connection lost (reason: {}). Reconnecting automatically...",
                disconnectResponse.getReturnCode());
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        logger.error("MQTT client error occurred", exception);
    }

    @Override
    public void authPacketArrived(int reasonCode, org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) {
        // No enhanced authentication configured — nothing to do.
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        logger.debug("MQTT message arrived on topic: {}, payload: {}", topic, payload);

        try {
            String[] segments = topic.split("/");
            if (segments.length < 3 || !"safelab".equalsIgnoreCase(segments[0])) {
                return;
            }

            String unit = segments[1];
            String type = segments[2];

            if ("status".equalsIgnoreCase(type)) {
                if ("OFFLINE".equalsIgnoreCase(payload)) {
                    transactionTemplate.executeWithoutResult(status -> {
                        String sensorName = unit; // Fallback to unit as sensor name
                        try {
                            applicationEventPublisher.publishEvent(new SensorUnavailable(
                                    unit, sensorName, LocalDateTime.now()));
                        } catch (Exception ex) {
                            logger.debug("Failed to publish SensorUnavailable event: {}", ex.getMessage());
                        }
                        logger.info("Sensor unit '{}' is now OFFLINE (LWT).", unit);
                    });
                }
            } else if ("data".equalsIgnoreCase(type)) {
                try {
                    String idemKey = "mqtt:" + unit + ":" + hash(payload);
                    if (idemKey != null) {
                        var existing = idempotencyRepository.findByIdempotencyKey(idemKey);
                        if (existing.isPresent() && existing.get().getExpiresAt() != null && existing.get().getExpiresAt().isAfter(LocalDateTime.now())) {
                            logger.debug("Skipping duplicate MQTT message for unit {}", unit);
                            return;
                        }
                    }
                    telemetryIngestionService.ingest(unit, payload);
                    if (idemKey != null) {
                        try {
                            TelemetryIngestionIdempotency idem = new TelemetryIngestionIdempotency();
                            idem.setIdempotencyKey(idemKey);
                            idem.setUnit(unit);
                            idem.setRequestHash(hash(payload));
                            idem.setResponseStatus(202);
                            idem.setResponseBody("{\"status\":\"ACCEPTED\"}");
                            idem.setExpiresAt(LocalDateTime.now().plusMinutes(idempotencyTtlMinutes));
                            idempotencyRepository.save(idem);
                        } catch (Exception ignored) {}
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("Rejected MQTT telemetry on topic '{}': {}", topic, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing MQTT message on topic: " + topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        // No-op (we only subscribe)
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return null;
        }
    }
}
