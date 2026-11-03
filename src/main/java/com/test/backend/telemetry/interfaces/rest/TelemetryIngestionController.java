package com.test.backend.telemetry.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.interfaces.rest.resources.TelemetryIngestionResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP ingestion endpoint (Iteration 1): alternative transport for edge
 * devices that cannot use MQTT. Authenticated with a shared {@code X-Api-Key}
 * header instead of a JWT (machine-to-machine).
 */
@Tag(name = "Telemetry Ingestion", description = "HTTP ingestion endpoint for edge devices (X-Api-Key authenticated)")
@RestController
@RequestMapping("/api/v1/telemetry/ingestion")
public class TelemetryIngestionController {

    private final TelemetryIngestionService telemetryIngestionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    public TelemetryIngestionController(TelemetryIngestionService telemetryIngestionService,
                                        @Value("${telemetry.ingestion.api-key:}") String apiKey) {
        this.telemetryIngestionService = telemetryIngestionService;
        this.apiKey = apiKey;
    }

    @PostMapping
    @Operation(
            summary = "Ingest a telemetry payload over HTTP",
            description = "Requires X-Api-Key header matching telemetry.ingestion.api-key. "
                    + "Payload follows the same shape as the MQTT telemetry topic."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Telemetry accepted and processed"),
            @ApiResponse(responseCode = "400", description = "Missing unit or metrics"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid X-Api-Key"),
            @ApiResponse(responseCode = "404", description = "Unregistered sensor unit"),
            @ApiResponse(responseCode = "503", description = "Ingestion API key not configured on the server")
    })
    public ResponseEntity<Map<String, String>> ingest(
            @RequestHeader(value = "X-Api-Key", required = false) String providedApiKey,
            @RequestBody TelemetryIngestionResource resource) {

        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "ERROR",
                    "message", "Telemetry ingestion is not configured (telemetry.ingestion.api-key is unset)."
            ));
        }
        if (providedApiKey == null || !apiKey.equals(providedApiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "ERROR",
                    "message", "Missing or invalid X-Api-Key."
            ));
        }
        if (resource.unit() == null || resource.unit().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Field 'unit' is required."
            ));
        }
        if (resource.metrics() == null || resource.metrics().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Field 'metrics' must contain at least one entry."
            ));
        }

        try {
            telemetryIngestionService.ingest(resource.unit(), toPayload(resource));
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ACCEPTED",
                    "unit", resource.unit()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to process telemetry: " + e.getMessage()
            ));
        }
    }

    /**
     * Rebuilds the canonical JSON payload so the HTTP transport feeds exactly
     * the same pipeline as MQTT.
     */
    private String toPayload(TelemetryIngestionResource resource) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("timestamp", resource.timestamp() != null ? resource.timestamp() : System.currentTimeMillis());
            envelope.put("metrics", resource.metrics());
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize telemetry payload", e);
        }
    }
}
