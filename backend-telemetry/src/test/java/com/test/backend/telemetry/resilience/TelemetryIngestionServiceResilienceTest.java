package com.test.backend.telemetry.resilience;

import com.test.backend.telemetry.application.internal.services.TelemetryIngestionServiceImpl;
import com.test.backend.telemetry.infrastructure.clients.SensorConfigurationClient;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.MetricTypeRepository;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.SensorReadingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ADR-004 wiring for the telemetry ingestion pipeline: the Feign call to the
 * automation service must run inside circuit breaker + retry, and the fallback
 * must distinguish client errors (propagated -> 404) from dependency failures
 * (swallowed -> degraded but available ingestion).
 */
class TelemetryIngestionServiceResilienceTest {

    private TelemetryIngestionServiceImpl newService() {
        return new TelemetryIngestionServiceImpl(
                Mockito.mock(SensorConfigurationClient.class),
                Mockito.mock(SensorReadingRepository.class),
                Mockito.mock(MetricTypeRepository.class),
                Mockito.mock(ApplicationEventPublisher.class));
    }

    @Test
    @DisplayName("ingest() esta protegido por @CircuitBreaker + @Retry con fallback")
    void ingest_IsProtectedByCircuitBreakerAndRetry() throws Exception {
        Method ingest = TelemetryIngestionServiceImpl.class
                .getMethod("ingest", String.class, String.class);

        CircuitBreaker cb = ingest.getAnnotation(CircuitBreaker.class);
        assertNotNull(cb, "ADR-004: ingestion must be guarded by a circuit breaker");
        assertEquals("labs-service", cb.name());
        assertEquals("ingestFallback", cb.fallbackMethod());

        Retry retry = ingest.getAnnotation(Retry.class);
        assertNotNull(retry, "ADR-004: transient failures must be retried");
        assertEquals("labs-service", retry.name());

        Method fallback = TelemetryIngestionServiceImpl.class.getMethod(
                "ingestFallback", String.class, String.class, Throwable.class);
        assertEquals(void.class, fallback.getReturnType());
    }

    @Test
    @DisplayName("El fallback re-lanza errores de cliente (sensor no registrado -> 404)")
    void fallback_RethrowsClientErrors() {
        TelemetryIngestionServiceImpl service = newService();
        IllegalArgumentException clientError =
                new IllegalArgumentException("Unregistered sensor unit: UNKNOWN-SENSOR");
        assertThrows(IllegalArgumentException.class,
                () -> service.ingestFallback("UNKNOWN-SENSOR", "{}", clientError),
                "client errors must reach the REST layer so it can answer 404");
    }

    @Test
    @DisplayName("El fallback absorbe fallos de dependencia (ingesta degradada, no caida)")
    void fallback_SwallowsDependencyFailures() {
        TelemetryIngestionServiceImpl service = newService();
        assertDoesNotThrow(
                () -> service.ingestFallback("Lab-04-TEMP-01", "{}",
                        new RuntimeException("Connect to safelab-backend-automation:8086 failed")),
                "when automation is down the pipeline must keep accepting telemetry (ADR-004)");
    }
}
