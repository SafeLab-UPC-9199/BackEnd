package com.test.backend.shared;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the backend-shared microservice (system status, outbox
 * dispatch and shared infrastructure). Its absence made
 * spring-boot-maven-plugin fail with "Unable to find main class", leaving a
 * plain (non-executable) jar that Docker could not start.
 */
@SpringBootApplication
// SharedEventOutboxDispatcher runs on @Scheduled but no module declares
// @EnableScheduling (only the monolith did), so outbox rows would never drain.
@EnableScheduling
public class SharedApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharedApplication.class, args);
    }
}
