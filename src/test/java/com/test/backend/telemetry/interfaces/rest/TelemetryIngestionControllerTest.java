package com.test.backend.telemetry.interfaces.rest;

import com.test.backend.telemetry.domain.services.TelemetryIngestionService;
import com.test.backend.telemetry.interfaces.rest.resources.TelemetryIngestionResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionControllerTest {

    private static final String API_KEY = "test-ingestion-key";

    @Mock
    private TelemetryIngestionService telemetryIngestionService;

    @InjectMocks
    private TelemetryIngestionController controller;

    private TelemetryIngestionController controllerWithKey(String key) {
        return new TelemetryIngestionController(telemetryIngestionService, key);
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
        var response = c.ingest(API_KEY, validResource());

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
        assertEquals(401, c.ingest(null, validResource()).getStatusCode().value());
        assertEquals(401, c.ingest("wrong-key", validResource()).getStatusCode().value());
        verifyNoInteractions(telemetryIngestionService);
    }

    @Test
    @DisplayName("ingest returns 400 when unit is missing (AAA)")
    void ingest_ShouldReturnBadRequest_WhenUnitMissing() {
        // Arrange
        var c = controllerWithKey(API_KEY);
        var resource = new TelemetryIngestionResource("  ", null, Map.of("temperature", 5.4));

        // Act
        var response = c.ingest(API_KEY, resource);

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
        var response = c.ingest(API_KEY, resource);

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
        var response = c.ingest(API_KEY, new TelemetryIngestionResource("NOPE", null, Map.of("temperature", 1.0)));

        // Assert
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("ingest returns 202 and delegates canonical payload when key valid (AAA)")
    void ingest_ShouldReturnAcceptedAndDelegate_WhenKeyValid() {
        // Arrange
        var c = controllerWithKey(API_KEY);

        // Act
        var response = c.ingest(API_KEY, validResource());

        // Assert
        assertEquals(202, response.getStatusCode().value());
        verify(telemetryIngestionService).ingest(eq("Lab-04"), argThat(payload ->
                payload.contains("\"timestamp\":1712345678900")
                        && payload.contains("\"temperature\":5.4")));
    }
}
