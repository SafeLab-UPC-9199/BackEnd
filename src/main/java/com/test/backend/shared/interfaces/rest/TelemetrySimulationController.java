package com.test.backend.shared.interfaces.rest;

import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.shared.application.CurrentWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Tag(name = "Telemetry Simulation", description = "Mock telemetry publisher API for development")
@RestController
@RequestMapping("/api/v1/telemetry/simulate")
public class TelemetrySimulationController {

    private final ObjectProvider<MqttClient> mqttClientProvider;
    private final CurrentWorkspaceService currentWorkspaceService;
    private final SensorConfigurationRepository sensorConfigurationRepository;

    public TelemetrySimulationController(ObjectProvider<MqttClient> mqttClientProvider,
                                         CurrentWorkspaceService currentWorkspaceService,
                                         SensorConfigurationRepository sensorConfigurationRepository) {
        this.mqttClientProvider = mqttClientProvider;
        this.currentWorkspaceService = currentWorkspaceService;
        this.sensorConfigurationRepository = sensorConfigurationRepository;
    }

    @PostMapping
    @Operation(summary = "Publish a mock telemetry payload to the MQTT broker")
    public ResponseEntity<Map<String, String>> simulateTelemetry(@RequestBody Map<String, Object> request) {
        // QAS-05: was permitAll - any anonymous caller could inject telemetry into any topic.
        // Administrator only, and the simulated unit must belong to the caller's workspace.
        if (!currentWorkspaceService.isCurrentUserAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", "ERROR",
                "message", "Only administrators can publish simulated telemetry."
            ));
        }

        String unit = request.getOrDefault("unit", "°C").toString();

        var workspaceIdOpt = currentWorkspaceService.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent()
                && !sensorConfigurationRepository.existsByLaboratoryWorkspaceIdAndUnit(workspaceIdOpt.get(), unit)) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ERROR",
                "message", "Unit '" + unit + "' is not registered in your workspace."
            ));
        }

        MqttClient mqttClient = mqttClientProvider.getIfAvailable();
        if (mqttClient == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "ERROR",
                "message", "MQTT is disabled on this deployment (mqtt.enabled=false)."
            ));
        }
        try {
            String metricName = request.getOrDefault("metric", "temperature").toString();
            double value = Double.parseDouble(request.getOrDefault("value", "5.4").toString());

            String topic = "safelab/" + unit + "/data";
            String payload = String.format(java.util.Locale.US, "{\"timestamp\":%d,\"metrics\":{\"%s\":%.2f}}",
                    System.currentTimeMillis(), metricName, value);

            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            mqttClient.publish(topic, message);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "topic", topic,
                "payload", payload
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
    }
}
