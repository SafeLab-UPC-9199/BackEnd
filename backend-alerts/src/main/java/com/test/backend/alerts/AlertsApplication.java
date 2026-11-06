package com.test.backend.alerts;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CorrelationIdFilter.class)
public class AlertsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertsApplication.class, args);
    }
}
