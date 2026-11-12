package com.test.backend.iam.resilience;

import com.test.backend.iam.infrastructure.email.resend.ResendClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-004 for backend-iam: the Resend email client is the only outbound
 * dependency of this context, so it must carry the circuit breaker + retry
 * configuration (the previously configured "labs-service" instance was dead
 * code — no annotation referenced it — and the real "resend" instance was
 * missing, which made resilience4j silently fall back to defaults).
 */
class IamResiliencePropertiesTest {

    private static Properties props;

    @BeforeAll
    static void loadProperties() throws Exception {
        props = new Properties();
        try (InputStream in = IamResiliencePropertiesTest.class
                .getResourceAsStream("/application.properties")) {
            assertNotNull(in, "backend-iam application.properties not found on the test classpath");
            props.load(in);
        }
    }

    @Test
    @DisplayName("Circuit breaker 'resend' cumple ADR-004 (10 / 50% / 30s / half-open)")
    void resendCircuitBreaker_MatchesAdr004() {
        String cb = "resilience4j.circuitbreaker.instances.resend.";
        assertEquals("10", props.getProperty(cb + "slidingWindowSize"));
        assertEquals("50", props.getProperty(cb + "failureRateThreshold"));
        assertEquals("30s", props.getProperty(cb + "waitDurationInOpenState"));
        assertEquals("5", props.getProperty(cb + "permittedNumberOfCallsInHalfOpenState"));
        assertEquals("true", props.getProperty(cb + "automaticTransitionFromOpenToHalfOpenEnabled"));
        assertEquals("true", props.getProperty(cb + "registerHealthIndicator"));
    }

    @Test
    @DisplayName("Retry de resend: 3 intentos con espera de 1s (ADR-004)")
    void resendRetry_MatchesAdr004() {
        String retry = "resilience4j.retry.instances.resend.";
        assertEquals("3", props.getProperty(retry + "maxAttempts"));
        assertEquals("1s", props.getProperty(retry + "waitDuration"));
    }

    @Test
    @DisplayName("No queda configuracion muerta de instancias sin anotaciones")
    void noDeadCircuitBreakerInstances() {
        assertFalse(props.containsKey("resilience4j.circuitbreaker.instances.labs-service.slidingWindowSize"),
                "labs-service has no @CircuitBreaker in backend-iam; it was dead configuration");
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
    @DisplayName("ResendClient anota @CircuitBreaker/@Retry sobre la instancia 'resend' configurada")
    void resendClientAnnotations_MatchConfiguredInstance() throws Exception {
        Method send = ResendClient.class.getMethod("send",
                com.resend.services.emails.model.CreateEmailOptions.class);

        CircuitBreaker cb = send.getAnnotation(CircuitBreaker.class);
        assertNotNull(cb, "ADR-004: ResendClient.send must be annotated with @CircuitBreaker");
        assertEquals("resend", cb.name());

        Retry retry = send.getAnnotation(Retry.class);
        assertNotNull(retry, "ADR-004: ResendClient.send must be annotated with @Retry");
        assertEquals("resend", retry.name());
    }
}
