package com.test.backend.telemetry;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.shared.infrastructure.messaging.outbox.SharedEventOutboxDispatcher;
import com.test.backend.telemetry.config.FeignClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Boot 4 moved @EntityScan out of spring-boot-autoconfigure
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Shared beans from backend-shared (outside this app's component scan):
// - MqttProperties: binds mqtt.*
// - JpaAuditingConfiguration: fills created_at/updated_at on every INSERT
// - RateLimitingFilter: 60 req/min on /api/v1/telemetry/ingestion (QAS)
// - SharedEventOutboxDispatcher: drains the outbox rows telemetry writes in the
//   same transaction as each reading (ADR-002) and hands them to the broker
//   publisher (ADR-005). It runs on @EnableScheduling, hence the annotation.
// NOTE: both basePackages lists must include this application's own package —
// when present, @EnableJpaRepositories and @EntityScan replace the
// auto-configured defaults (same pattern as AlertsApplication).
@Import({CorrelationIdFilter.class, FeignClientConfig.class,
        com.test.backend.shared.infrastructure.persistence.configuration.MqttProperties.class,
        com.test.backend.shared.infrastructure.persistence.configuration.JpaAuditingConfiguration.class,
        com.test.backend.shared.infrastructure.filter.RateLimitingFilter.class,
        SharedEventOutboxDispatcher.class})
@EnableScheduling
@EnableJpaRepositories(basePackages = {
        "com.test.backend.telemetry",
        "com.test.backend.shared.infrastructure.persistence.jpa.repositories"
})
@EntityScan(basePackages = {
        "com.test.backend.telemetry",
        "com.test.backend.shared.domain.model"
})
public class TelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryApplication.class, args);
    }
}
