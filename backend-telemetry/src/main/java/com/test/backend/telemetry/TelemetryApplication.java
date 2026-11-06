package com.test.backend.telemetry;

import com.test.backend.shared.infrastructure.logging.CorrelationIdFilter;
import com.test.backend.telemetry.config.FeignClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({CorrelationIdFilter.class, FeignClientConfig.class})
public class TelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryApplication.class, args);
    }
}
