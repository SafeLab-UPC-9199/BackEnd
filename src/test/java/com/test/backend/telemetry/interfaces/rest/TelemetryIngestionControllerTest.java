package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.TelemetryIngestionIdempotencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import com.test.backend.telemetry.domain.model.entities.TelemetryIngestionIdempotency;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionControllerTest {

    private static final String API_KEY = "test-ingestion-key";
    private static final String HMAC_SECRET = "unit-test-hmac-secret";
    private static final String DEVICE_SECRET = "per-device-hmac-secret";

    @Mock
    private TelemetryIngestionService telemetryIngestionService;

    @Mock
    private TelemetryIngestionIdempotencyRepository idempotencyRepository;

    @Mock
    private SensorConfigurationRepository sensorConfigurationRepository;

    private TelemetryIngestionController controllerWith(String key, String hmacSecret) {
        return new TelemetryIngestionController(telemetryIngestionService, key, hmacSecret,
                idempotencyRepository, sensorConfigurationRepository, 60L);
    }

    private TelemetryIngestionController controllerWithKey(String key) {
        return controllerWith(key, "");
    }

    private String validBody() {
        return "{\"unit\":\"Lab-04\",\"timestamp\":1712345678900,\"metrics\":{\"temperature\":5.4}}";
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("ingest returns 503 when API key is not configured (AAA)")
    void ingest_ShouldReturnServiceUnavailable_WhenKeyNotConfigured() {
        // Arrange
        var c = controllerWith("", "");

        // Act
        var response = c.ingest(API_KEY, null, null, validBody());

        // Assert
        assertEquals(503, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 401 when X-Api-Key is missing or wrong (AAA)")
    void ingest_ShouldReturnUnauthorized_WhenKeyInvalid() {
        // Arrange
        var c = controllerWithKey(API_KEY);

        // Act & Assert
        assertEquals(401, c.ingest(null, null, null, validBody()).getStatusCode().value());
        assertEquals(401, c.ingest("wrong-key", null, null, validBody()).getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when unit is missing (AAA)")
    void ingest_ShouldReturnBadRequest_WhenUnitMissing() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var body = "{\"unit\":\"  \",\"metrics\":{\"temperature\":5.4}}";

        // Act
        var response = c.ingest(API_KEY, null, null, body);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when metrics are empty (AAA)")
    void ingest_ShouldReturnBadRequest_WhenMetricsEmpty() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var body = "{\"unit\":\"Lab-04\",\"metrics\":{}}";

        // Act
        var response = c.ingest(API_KEY, null, null, body);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when the body is not valid JSON (AAA)")
    void ingest_ShouldReturnBadRequest_WhenBodyMalformed() {
        // Arrange
        var c = controllerWithKey(API_KEY);

        // Act
        var response = c.ingest(API_KEY, null, null, "{oops");

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 404 when sensor unit is unregistered (AAA)")
    void ingest_ShouldReturnNotFound_WhenUnitUnregistered() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        doThrow(new IllegalArgumentException("Unregistered sensor unit: NOPE"))
                .when(telemetryIngestionService).ingest(eq("NOPE"), any());

        // Act
        var response = c.ingest(API_KEY, null, null,
                "{\"unit\":\"NOPE\",\"metrics\":{\"temperature\":1.0}}");

        // Assert
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("ingest returns 202 and delegates the raw frame when key valid (AAA)")
    void ingest_ShouldReturnAcceptedAndDelegate_WhenKeyValid() {
        // Arrange
        var c = controllerWithKey(API_KEY);

        // Act
        var response = c.ingest(API_KEY, null, null, validBody());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), argThat(payload ->
                payload.contains("\"timestamp\":1712345678900")
                        && payload.contains("\"temperature\":5.4")));
    }

    @Test
    @DisplayName("ingest returns 401 when signature is required but missing (AAA)")
    void ingest_ShouldReturnUnauthorized_WhenSignatureMissing() {
        // Arrange
        var c = controllerWith(API_KEY, HMAC_SECRET);

        // Act
        var response = c.ingest(API_KEY, null, null, validBody());

        // Assert
        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("X-Signature"));
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 401 when the device signature is wrong (AAA)")
    void ingest_ShouldReturnUnauthorized_WhenSignatureInvalid() {
        // Arrange
        var c = controllerWith(API_KEY, HMAC_SECRET);

        // Act
        var response = c.ingest(API_KEY, null, "deadbeef", validBody());

        // Assert
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 202 when the device signature is valid (AAA)")
    void ingest_ShouldReturnAccepted_WhenSignatureValid() {
        // Arrange
        var c = controllerWith(API_KEY, HMAC_SECRET);
        var signature = sign(HMAC_SECRET, validBody());

        // Act
        var response = c.ingest(API_KEY, null, signature, validBody());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), any());
    }

    @Test
    @DisplayName("ingest returns cached 202 without delegating when Idempotency-Key already exists (AAA)")
    void ingest_ShouldReturnCachedResponse_WhenIdempotencyKeyReplay() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var existing = new TelemetryIngestionIdempotency();
        existing.setIdempotencyKey("key-1");
        existing.setUnit("Lab-04");
        existing.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(idempotencyRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // Act
        var response = c.ingest(API_KEY, "key-1", null, validBody());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        assertEquals("true", response.getBody().get("idempotent"));
        verifyNoInteractions(telemetryIngestionService);
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingest persists an idempotency record when the key is new (AAA)")
    void ingest_ShouldPersistIdempotencyRecord_WhenKeyIsNew() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        when(idempotencyRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());

        // Act
        var response = c.ingest(API_KEY, "key-2", null, validBody());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), any());
        verify(idempotencyRepository).save(argThat(idem ->
                "key-2".equals(idem.getIdempotencyKey())
                        && "Lab-04".equals(idem.getUnit())
                        && idem.getRequestHash() != null
                        && idem.getExpiresAt() != null
                        && idem.getExpiresAt().isAfter(LocalDateTime.now())));
    }

    private SensorConfiguration sensorWithDeviceSecret() {
        var sensor = new SensorConfiguration();
        sensor.setUnit("Lab-04");
        sensor.setDeviceSecret(DEVICE_SECRET);
        return sensor;
    }

    @Test
    @DisplayName("per-device signature: frame signed with the device's own key is accepted (AAA)")
    void ingest_ShouldAcceptSignatureOfTheDevicesOwnKey() {
        // Arrange - sensor carries its own key; the shared secret does not apply to it
        var c = controllerWith(API_KEY, HMAC_SECRET);
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithDeviceSecret()));
        var signature = sign(DEVICE_SECRET, validBody());

        // Act
        var response = c.ingest(API_KEY, null, signature, validBody());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), any());
    }

    @Test
    @DisplayName("per-device signature: the shared deployment secret does NOT satisfy a device that has its own key (AAA)")
    void ingest_ShouldRejectSharedSecretForDeviceWithOwnKey() {
        // Arrange - attacker replaying frames with the leaked shared secret
        var c = controllerWith(API_KEY, HMAC_SECRET);
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithDeviceSecret()));
        var sharedSecretSignature = sign(HMAC_SECRET, validBody());

        // Act
        var response = c.ingest(API_KEY, null, sharedSecretSignature, validBody());

        // Assert - fail-closed on the device's own key
        assertEquals(401, response.getStatusCode().value());
        assertTrue(response.getBody().get("message").contains("own key"));
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("per-device signature is enforced even when no shared secret is configured (AAA)")
    void ingest_ShouldEnforceDeviceSignatureWithoutSharedSecret() {
        // Arrange - device secret alone requires the signature
        var c = controllerWithKey(API_KEY);
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithDeviceSecret()));

        // Act
        var response = c.ingest(API_KEY, null, null, validBody());

        // Assert
        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }
}
