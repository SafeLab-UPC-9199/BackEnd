package com.test.backend.iam.infrastructure.email.resend;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Outbound Resend API client with resilience4j protection (Iteration 1):
 * transient failures are retried and sustained failures open the circuit so
 * callers fail fast instead of piling up threads against a down dependency.
 *
 * Kept as a separate bean (called from the async email facade) so the
 * resilience annotations are always applied through the Spring proxy.
 */
@Component
public class ResendClient {

    private static final Logger logger = LoggerFactory.getLogger(ResendClient.class);

    private final Resend resend;

    public ResendClient(@Value("${resend.api-key}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Resend API key is not configured. Outbound email delivery will be disabled.");
        } else {
            logger.info("Resend API Key loaded successfully. Value: {}...", apiKey.substring(0, Math.min(apiKey.length(), 10)));
        }
        this.resend = new Resend(apiKey);
    }

    @Retry(name = "resend")
    @CircuitBreaker(name = "resend")
    public CreateEmailResponse send(CreateEmailOptions options) throws ResendException {
        return resend.emails().send(options);
    }
}
