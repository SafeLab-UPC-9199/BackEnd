package com.test.backend.telemetry;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.telemetry.config.FeignClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
// Shared beans from backend-shared (outside this app's component scan):
// - MqttProperties: binds mqtt.*
// - JpaAuditingConfiguration: fills created_at/updated_at on every INSERT
// - RateLimitingFilter: 60 req/min on /api/v1/telemetry/ingestion (QAS)
@Import({CorrelationIdFilter.class, FeignClientConfig.class,
        com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties.class,
        com.test.backend.shared.infrastructure.persistence.configuration.JpaAuditingConfiguration.class,
        com.test.backend.shared.infrastructure.filter.RateLimitingFilter.class})
public class TelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryApplication.class, args);
    }
}
