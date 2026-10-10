package com.test.backend.shared.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "System Status", description = "System and Infrastructure Health Status API")
@RestController
@RequestMapping("/api/v1/system/status")
public class SystemStatusController {

    private final ObjectProvider<MqttClient> mqttClientProvider;

    public SystemStatusController(ObjectProvider<MqttClient> mqttClientProvider) {
        this.mqttClientProvider = mqttClientProvider;
    }

    @GetMapping
    @Operation(summary = "Get connection status between backend and MQTT broker")
    public ResponseEntity<Map<String, String>> getSystemStatus() {
        MqttClient mqttClient = mqttClientProvider.getIfAvailable();
        String status = (mqttClient != null && mqttClient.isConnected()) ? "CONNECTED" : "DISCONNECTED";
        return ResponseEntity.ok(Map.of("status", status));
    }
}
