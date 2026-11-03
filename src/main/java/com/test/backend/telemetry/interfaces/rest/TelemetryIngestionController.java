package com.test.backend.telemetry.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.interfaces.rest.resources.TelemetryIngestionResource;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.TelemetryIngestionIdempotencyRepository;
import com.test.backend.telemetry.domain.model.entities.TelemetryIngestionIdempotency;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    private final TelemetryIngestionIdempotencyRepository idempotencyRepository;
    private final long idempotencyTtlMinutes;

    public TelemetryIngestionController(TelemetryIngestionService telemetryIngestionService,
                                        @Value("${telemetry.ingestion.api-key:}") String apiKey,
                                        TelemetryIngestionIdempotencyRepository idempotencyRepository,
                                        @Value("${telemetry.idempotency.ttl-minutes:60}") long idempotencyTtlMinutes) {
        this.telemetryIngestionService = telemetryIngestionService;
        this.apiKey = apiKey;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencyTtlMinutes = idempotencyTtlMinutes;
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
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

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<TelemetryIngestionIdempotency> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                TelemetryIngestionIdempotency idem = existing.get();
                if (idem.getExpiresAt() != null && idem.getExpiresAt().isAfter(LocalDateTime.now())) {
                    Map<String, String> cached = new LinkedHashMap<>();
                    cached.put("status", "ACCEPTED");
                    cached.put("idempotent", "true");
                    return ResponseEntity.accepted().body(cached);
                }
            }
        }

        try {
            String payloadJson = toPayload(resource);
            telemetryIngestionService.ingest(resource.unit(), payloadJson);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                TelemetryIngestionIdempotency idem = new TelemetryIngestionIdempotency();
                idem.setIdempotencyKey(idempotencyKey);
                idem.setUnit(resource.unit());
                idem.setRequestHash(hash(payloadJson));
                idem.setResponseStatus(202);
                idem.setResponseBody("{\"status\":\"ACCEPTED\"}");
                idem.setCreatedAt(LocalDateTime.now());
                idem.setExpiresAt(LocalDateTime.now().plusMinutes(idempotencyTtlMinutes));
                try { idempotencyRepository.save(idem); } catch (Exception ignored) {}
            }
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
