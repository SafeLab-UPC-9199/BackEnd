package com.test.backend.telemetry.resilience;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-004 parameterization of the telemetry service: circuit breaker window,
 * failure threshold, open-state wait, ignored client errors, retry policy,
 * rate limit on the ingestion endpoint and actuator exposure of the
 * resilience metrics.
 */
class TelemetryResiliencePropertiesTest {

    private static Properties props;

    @BeforeAll
    static void loadProperties() throws Exception {
        props = new Properties();
        try (InputStream in = TelemetryResiliencePropertiesTest.class
                .getResourceAsStream("/application.properties")) {
            assertNotNull(in, "backend-telemetry application.properties not found on the test classpath");
            props.load(in);
        }
    }

    @Test
    @DisplayName("Circuit breaker 'labs-service' cumple ADR-004 (10 / 50% / 30s / half-open)")
    void circuitBreaker_MatchesAdr004() {
        String cb = "resilience4j.circuitbreaker.instances.labs-service.";
        assertEquals("10", props.getProperty(cb + "slidingWindowSize"));
        assertEquals("50", props.getProperty(cb + "failureRateThreshold"));
        assertEquals("30s", props.getProperty(cb + "waitDurationInOpenState"));
        assertEquals("3", props.getProperty(cb + "permittedNumberOfCallsInHalfOpenState"));
        assertEquals("true", props.getProperty(cb + "automaticTransitionFromOpenToHalfOpenEnabled"));
        assertEquals("true", props.getProperty(cb + "registerHealthIndicator"));
    }

    @Test
    @DisplayName("Los errores de cliente no cuentan como fallo de dependencia (ni en CB ni en Retry)")
    void clientErrors_AreIgnoredByCircuitBreakerAndRetry() {
        String expected = "java.lang.IllegalArgumentException,java.lang.IllegalStateException";
        String cbIgnore = props.getProperty(
                "resilience4j.circuitbreaker.instances.labs-service.ignoreExceptions");
        assertEquals(expected, cbIgnore,
                "a burst of unknown units must not open the circuit against automation");
        String retryIgnore = props.getProperty(
                "resilience4j.retry.instances.labs-service.ignoreExceptions");
        assertEquals(expected, retryIgnore,
                "a 404 must not be retried 3 times (2s of wasted latency)");
    }

    @Test
    @DisplayName("Retry: 3 intentos con espera de 1s (ADR-004)")
    void retry_MatchesAdr004() {
        String retry = "resilience4j.retry.instances.labs-service.";
        assertEquals("3", props.getProperty(retry + "maxAttempts"));
        assertEquals("1s", props.getProperty(retry + "waitDuration"));
    }

    @Test
    @DisplayName("Rate limiting del endpoint de ingesta: 60 req/min (ADR-004)")
    void rateLimiter_MatchesAdr004() {
        String rl = "resilience4j.ratelimiter.instances.telemetry-ingestion.";
        assertEquals("60", props.getProperty(rl + "limitForPeriod"));
        assertEquals("60s", props.getProperty(rl + "limitRefreshPeriod"));
    }

    @Test
    @DisplayName("Actuator expone circuitbreakers, circuitbreakerevents y retries (ADR-004)")
    void actuatorExposesResilienceEndpoints() {
        String exposure = props.getProperty("management.endpoints.web.exposure.include", "");
        assertTrue(exposure.contains("circuitbreakers"), "missing circuitbreakers: " + exposure);
        assertTrue(exposure.contains("circuitbreakerevents"), "missing circuitbreakerevents: " + exposure);
        assertTrue(exposure.contains("retries"), "missing retries: " + exposure);
    }

    @Test
    @DisplayName("La URL de automation esta externalizada con default de compose")
    void automationServiceUrl_IsExternalized() {
        assertEquals("${AUTOMATION_SERVICE_URL:http://safelab-backend-automation:8086}",
                props.getProperty("automation.service.url"));
    }
}
