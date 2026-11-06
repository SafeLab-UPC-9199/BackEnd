package com.test.backend.iam.application.internal.eventhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Temporary Demo Seeder - STUBBED for microservice boundary.
 * Original seeder had cross-context dependencies on labs, telemetry, alerts,
 * compliance, history, and automation services. In the microservices architecture,
 * each service seeds its own data independently.
 */
@Component
@Order(2)
public class TemporarySeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(TemporarySeeder.class);

    @Override
    public void run(String... args) {
        logger.info("TemporarySeeder is stubbed in microservices architecture. "
                + "Each service seeds its own data independently.");
    }
}
