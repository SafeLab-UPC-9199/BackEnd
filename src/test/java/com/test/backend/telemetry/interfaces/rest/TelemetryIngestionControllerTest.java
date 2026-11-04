package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.telemetry.domain.model.entities.TelemetryIngestionIdempotency;
import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.interfaces.rest.resources.TelemetryIngestionResource;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.TelemetryIngestionIdempotencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionControllerTest {

    private static final String API_KEY = "test-ingestion-key";

    @Mock
    private TelemetryIngestionService telemetryIngestionService;

    @Mock
    private TelemetryIngestionIdempotencyRepository idempotencyRepository;

    private TelemetryIngestionController controllerWithKey(String key) {
        return new TelemetryIngestionController(telemetryIngestionService, key, idempotencyRepository, 60L);
    }

    private TelemetryIngestionResource validResource() {
        return new TelemetryIngestionResource("Lab-04", 1712345678900L, Map.of("temperature", 5.4));
    }

    @Test
    @DisplayName("ingest returns 503 when API key is not configured (AAA)")
    void ingest_ShouldReturnServiceUnavailable_WhenKeyNotConfigured() {
        // Arrange
        var c = controllerWithKey("");

        // Act
        var response = c.ingest(API_KEY, null, validResource());

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
        assertEquals(401, c.ingest(null, null, validResource()).getStatusCode().value());
        assertEquals(401, c.ingest("wrong-key", null, validResource()).getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when unit is missing (AAA)")
    void ingest_ShouldReturnBadRequest_WhenUnitMissing() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var resource = new TelemetryIngestionResource("  ", null, Map.of("temperature", 5.4));

        // Act
        var response = c.ingest(API_KEY, null, resource);

        // Assert
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when metrics are empty (AAA)")
    void ingest_ShouldReturnBadRequest_WhenMetricsEmpty() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var resource = new TelemetryIngestionResource("Lab-04", null, Map.of());

        // Act
        var response = c.ingest(API_KEY, null, resource);

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
        var response = c.ingest(API_KEY, null, new TelemetryIngestionResource("NOPE", null, Map.of("temperature", 1.0)));

        // Assert
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("ingest returns 202 and delegates canonical payload when key valid (AAA)")
    void ingest_ShouldReturnAcceptedAndDelegate_WhenKeyValid() {
        // Arrange
        var c = controllerWithKey(API_KEY);

        // Act
        var response = c.ingest(API_KEY, null, validResource());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), argThat(payload ->
                payload.contains("\"timestamp\":1712345678900")
                        && payload.contains("\"temperature\":5.4")));
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
        var response = c.ingest(API_KEY, "key-1", validResource());

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
        var response = c.ingest(API_KEY, "key-2", validResource());

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
}
