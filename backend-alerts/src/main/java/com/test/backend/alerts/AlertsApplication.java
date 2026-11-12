package com.test.backend.alerts;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.shared.infrastructure.persistence.configuration.JpaAuditingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Boot 4 moved @EntityScan out of spring-boot-autoconfigure
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
// The outbox (SharedEventOutboxRepository + its entity, both under
// com.test.backend.shared) lives in backend-shared/shared-kernel, outside the
// component scan of this application. Alerts is the only service that writes
// to the outbox, so it explicitly enables them. NOTE: both basePackages lists
// must include this application's own package — when present, @EnableJpaRepositories
// and @EntityScan replace the auto-configured defaults.
@EnableJpaRepositories(basePackages = {
        "com.test.backend.alerts",
        "com.test.backend.shared.infrastructure.persistence.jpa.repositories"
})
@EntityScan(basePackages = {
        "com.test.backend.alerts",
        "com.test.backend.shared.domain.model"
})
@Import({CorrelationIdFilter.class, JpaAuditingConfiguration.class})
public class AlertsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertsApplication.class, args);
    }
}
