package com.test.backend.automation.application.internal.eventhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * STUBBED: Original handler had cross-context dependencies on IAM events and Labs repositories.
 * In the microservices architecture, user registration events are handled by each service
 * independently via their own event consumers.
 */
@Component
public class UserRegisteredEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserRegisteredEventHandler.class);

    @EventListener
    @Transactional
    public void on(Object event) {
        logger.debug("UserRegisteredEventHandler received event (stubbed): {}", event);
    }
}
