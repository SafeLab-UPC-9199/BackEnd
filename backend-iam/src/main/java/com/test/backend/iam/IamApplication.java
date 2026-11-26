package com.test.backend.iam;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.shared.infrastructure.messaging.outbox.SharedEventOutboxDispatcher;
import com.test.backend.shared.infrastructure.persistence.configuration.JpaAuditingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({CorrelationIdFilter.class, JpaAuditingConfiguration.class, SharedEventOutboxDispatcher.class})
@EnableJpaRepositories(basePackages = {
        "com.test.backend.iam",
        "com.test.backend.shared.infrastructure.persistence.jpa.repositories"
})
@EntityScan(basePackages = {
        "com.test.backend.iam",
        "com.test.backend.shared.domain.model"
})
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
