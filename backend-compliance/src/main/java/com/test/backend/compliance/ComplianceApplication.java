package com.test.backend.compliance;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.shared.infrastructure.persistence.configuration.JpaAuditingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
// JpaAuditingConfiguration lives in backend-shared, outside this service's
// component scan; without it @CreatedDate/@LastModifiedDate stay null and every
// INSERT fails with "Column 'created_at' cannot be null".
@Import({CorrelationIdFilter.class, JpaAuditingConfiguration.class})
public class ComplianceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceApplication.class, args);
    }
}
