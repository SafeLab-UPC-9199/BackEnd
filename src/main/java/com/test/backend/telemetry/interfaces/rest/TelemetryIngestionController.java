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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import java.util.Optional;

/**
 * HTTP ingestion endpoint (Iteration 1): alternative transport for edge
 * devices that cannot use MQTT. Authenticated with a shared {@code X-Api-Key}
 * header instead of a JWT (machine-to-machine), and — when
 * {@code telemetry.ingestion.hmac-secret} is configured — the device signature
 * {@code X-Signature} (hex HMAC-SHA256 of the raw body) is verified before the
 * frame is injected into the canonical pipeline.
 */
@Tag(name = "Telemetry Ingestion", description = "HTTP ingestion endpoint for edge devices (X-Api-Key + optional X-Signature)")
@RestController
@RequestMapping("/api/v1/telemetry/ingestion")
public class TelemetryIngestionController {

    private final TelemetryIngestionService telemetryIngestionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String hmacSecret;
    private final TelemetryIngestionIdempotencyRepository idempotencyRepository;
    private final long idempotencyTtlMinutes;

    public TelemetryIngestionController(TelemetryIngestionService telemetryIngestionService,
                                        @Value("${telemetry.ingestion.api-key:}") String apiKey,
                                        @Value("${telemetry.ingestion.hmac-secret:}") String hmacSecret,
                                        TelemetryIngestionIdempotencyRepository idempotencyRepository,
                                        @Value("${telemetry.idempotency.ttl-minutes:60}") long idempotencyTtlMinutes) {
        this.telemetryIngestionService = telemetryIngestionService;
        this.apiKey = apiKey;
        this.hmacSecret = hmacSecret;
        this.idempotencyRepository = idempotencyRepository;
        this.idempotencyTtlMinutes = idempotencyTtlMinutes;
    }

    @PostMapping
    @Operation(
            summary = "Ingest a telemetry payload over HTTP",
            description = "Requires X-Api-Key header matching telemetry.ingestion.api-key. When "
                    + "telemetry.ingestion.hmac-secret is set, also requires X-Signature (hex HMAC-SHA256 "
                    + "of the raw body). Payload follows the same shape as the MQTT telemetry topic."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Telemetry accepted and processed"),
            @ApiResponse(responseCode = "400", description = "Missing/malformed body, unit or metrics"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid X-Api-Key or X-Signature"),
            @ApiResponse(responseCode = "404", description = "Unregistered sensor unit"),
            @ApiResponse(responseCode = "503", description = "Ingestion API key not configured on the server")
    })
    public ResponseEntity<Map<String, String>> ingest(
            @RequestHeader(value = "X-Api-Key", required = false) String providedApiKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) {

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
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Request body is required."
            ));
        }

        TelemetryIngestionResource resource;
        try {
            resource = objectMapper.readValue(rawBody, TelemetryIngestionResource.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Malformed JSON payload."
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

        // Device signature (integrity of the frame) — fail-closed when configured.
        if (hmacConfigured() && !isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "ERROR",
                    "message", "Missing or invalid X-Signature (HMAC-SHA256 of the raw body)."
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
            telemetryIngestionService.ingest(resource.unit(), rawBody);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                TelemetryIngestionIdempotency idem = new TelemetryIngestionIdempotency();
                idem.setIdempotencyKey(idempotencyKey);
                idem.setUnit(resource.unit());
                idem.setRequestHash(hash(rawBody));
                idem.setResponseStatus(202);
                idem.setResponseBody("{\"status\":\"ACCEPTED\"}");
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

    private boolean hmacConfigured() {
        return hmacSecret != null && !hmacSecret.isBlank();
    }

    /**
     * Verifies {@code X-Signature} = hex(HMAC-SHA256(rawBody, secret)) using a
     * constant-time comparison. Skipped (returns true) when no secret is
     * configured, for backwards compatibility with existing deployments.
     */
    private boolean isValidSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
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
