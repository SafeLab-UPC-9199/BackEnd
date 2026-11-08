package com.test.backend.resilience;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-004 (Tacticas de resiliencia) — contract tests for the monolith:
 * the Resend outbound client must be protected by a circuit breaker + retry
 * whose properties match the ADR (window=10, threshold=50%, wait=30s,
 * half-open) and whose metrics are exposed through Actuator.
 */
class ResilienceConfigurationTest {

    private static Properties props;

    @BeforeAll
    static void loadProperties() throws Exception {
        props = new Properties();
        try (InputStream in = ResilienceConfigurationTest.class
                .getResourceAsStream("/application.properties")) {
            assertNotNull(in, "monolith application.properties not found on the test classpath");
            props.load(in);
        }
    }

    @Test
    @DisplayName("Resend circuit breaker follows ADR-004 (window=10, 50%, 30s, half-open automatico)")
    void resendCircuitBreaker_MatchesAdr004() {
        String cb = "resilience4j.circuitbreaker.instances.resend.";
        assertEquals("10", props.getProperty(cb + "sliding-window-size"));
        assertEquals("50", props.getProperty(cb + "failure-rate-threshold"));
        assertEquals("30s", props.getProperty(cb + "wait-duration-in-open-state"));
        assertEquals("5", props.getProperty(cb + "permitted-number-of-calls-in-half-open-state"));
        assertEquals("true", props.getProperty(cb + "automatic-transition-from-open-to-half-open-enabled"),
                "ADR-004 requires automatic transition to half-open");
        assertEquals("true", props.getProperty(cb + "register-health-indicator"),
                "ADR-004: circuit breaker health must be visible via Actuator");
    }

    @Test
    @DisplayName("Retry de Resend: 3 intentos con espera de 1s (ADR-004)")
    void resendRetry_MatchesAdr004() {
        String retry = "resilience4j.retry.instances.resend.";
        assertEquals("3", props.getProperty(retry + "max-attempts"));
        assertEquals("1s", props.getProperty(retry + "wait-duration"));
    }

    @Test
    @DisplayName("Actuator expone los endpoints de circuit breaker y retry (ADR-004)")
    void actuatorExposesResilienceEndpoints() {
        String exposure = props.getProperty("management.endpoints.web.exposure.include", "");
        assertTrue(exposure.contains("circuitbreakers"), "missing circuitbreakers endpoint: " + exposure);
        assertTrue(exposure.contains("circuitbreakerevents"), "missing circuitbreakerevents endpoint: " + exposure);
        assertTrue(exposure.contains("retries"), "missing retries endpoint: " + exposure);
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

        // The annotated instance must actually be configured, otherwise
        // resilience4j would silently fall back to defaults.
        assertTrue(props.containsKey("resilience4j.circuitbreaker.instances.resend.sliding-window-size"),
                "instance 'resend' referenced by the annotation is not configured");
        assertTrue(props.containsKey("resilience4j.retry.instances.resend.max-attempts"),
                "retry instance 'resend' referenced by the annotation is not configured");
    }
}
