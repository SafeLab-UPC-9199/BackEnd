package com.test.backend.telemetry.infrastructure.mqtt;

import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * MQTT transport adapter: parses topic/payload and delegates the canonical
 * normalization pipeline to {@link TelemetryIngestionService}, which is shared
 * with the HTTP ingestion endpoint.
 */
@Service
@ConditionalOnProperty(prefix = "mqtt", name = "enabled", havingValue = "true")
public class MqttTelemetrySubscriber implements MqttCallbackExtended {

    private static final Logger logger = LoggerFactory.getLogger(MqttTelemetrySubscriber.class);

    private final MqttClient client;
    private final MqttConnectOptions connectOptions;
    private final MqttProperties mqttProperties;
    private final SensorConfigurationRepository sensorConfigurationRepository;
    private final TransactionTemplate transactionTemplate;
    private final TelemetryIngestionService telemetryIngestionService;

    public MqttTelemetrySubscriber(MqttClient client,
                                   MqttConnectOptions connectOptions,
                                   MqttProperties mqttProperties,
                                   SensorConfigurationRepository sensorConfigurationRepository,
                                   PlatformTransactionManager transactionManager,
                                   TelemetryIngestionService telemetryIngestionService) {
        this.client = client;
        this.connectOptions = connectOptions;
        this.mqttProperties = mqttProperties;
        this.sensorConfigurationRepository = sensorConfigurationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.telemetryIngestionService = telemetryIngestionService;
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
            logger.info("Attempting to connect to MQTT broker: {}", client.getServerURI());
            client.connect(connectOptions);
            logger.info("Successfully connected to MQTT broker.");
        } catch (MqttException e) {
            logger.error("Failed to connect to MQTT broker. Single attempt finished (will not retry to avoid log spam).", e);
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
    public void connectionLost(Throwable cause) {
        logger.warn("MQTT connection lost! Reconnecting automatically...", cause);
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
                        Optional<SensorConfiguration> sensorOpt = sensorConfigurationRepository.findByUnit(unit);
                        if (sensorOpt.isPresent()) {
                            SensorConfiguration sensor = sensorOpt.get();
                            sensor.setStatus("OFFLINE");
                            sensorConfigurationRepository.save(sensor);
                            logger.info("Sensor unit '{}' is now OFFLINE (LWT).", unit);
                        }
                    });
                }
            } else if ("data".equalsIgnoreCase(type)) {
                try {
                    telemetryIngestionService.ingest(unit, payload);
                } catch (IllegalArgumentException e) {
                    logger.warn("Rejected MQTT telemetry on topic '{}': {}", topic, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error processing MQTT message on topic: " + topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No-op (we only subscribe)
    }
}
