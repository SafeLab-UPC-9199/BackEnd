package com.test.backend.shared.infrastructure.messaging.outbox;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class SharedEventOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(SharedEventOutboxDispatcher.class);

    @Value("${outbox.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.purge-after-days:7}")
    private int purgeAfterDays;

    /**
     * Comma-separated list of event types this instance is allowed to drain.
     * The outbox is a single shared table, so without this filter every service
     * running the dispatcher would race for every row: the shared service would
     * mark telemetry's readings as PUBLISHED without ever handing them to the
     * broker. Empty (default) = drain everything, i.e. the original behaviour.
     */
    @Value("${outbox.dispatch-event-types:}")
    private String dispatchEventTypes;

    private final SharedEventOutboxRepository sharedEventOutboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public SharedEventOutboxDispatcher(SharedEventOutboxRepository sharedEventOutboxRepository,
                                       ApplicationEventPublisher applicationEventPublisher) {
        this.sharedEventOutboxRepository = sharedEventOutboxRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedDelayString = "${outbox.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatchPendingEvents() {
        var pendingEvents = sharedEventOutboxRepository.findByStatus("PENDING");
        for (var event : pendingEvents) {
            if (!isResponsibleFor(event.getEventType())) {
                continue;
            }
            dispatch(event);
        }
    }

    /**
     * True when this instance owns {@code eventType}. See
     * {@code outbox.dispatch-event-types}: each service drains only the event
     * types it publishes, so no two dispatchers can mark the same row
     * PUBLISHED (at-least-once delivery depends on a single owner per type).
     */
    private boolean isResponsibleFor(String eventType) {
        if (dispatchEventTypes == null || dispatchEventTypes.isBlank()) {
            return true;
        }
        for (String owned : dispatchEventTypes.split(",")) {
            if (eventType != null && eventType.equals(owned.trim())) {
                return true;
            }
        }
        logger.debug("Skipping outbox event {}: owned by another service", eventType);
        return false;
    }

    @Scheduled(cron = "${outbox.purge-cron:0 0 2 * * *}")
    @Transactional
    public void purgePublishedEvents() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(purgeAfterDays);
            int deleted = sharedEventOutboxRepository.deletePublishedBefore(cutoff);
            if (deleted > 0) {
                logger.info("Purged {} published outbox events older than {}", deleted, cutoff);
            }
        } catch (Exception ex) {
            logger.warn("Failed to purge published outbox events: {}", ex.getMessage());
        }
    }

    private void dispatch(SharedEventOutbox event) {
        try {
            applicationEventPublisher.publishEvent(event);
            event.setStatus("PUBLISHED");
            event.setLastError(null);
            sharedEventOutboxRepository.save(event);
        } catch (Exception ex) {
            int attempt = event.getRetryCount() + 1;
            event.setRetryCount(attempt);
            String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            event.setLastError(err.length() > 1000 ? err.substring(0, 1000) : err);
            if (attempt >= maxRetries) {
                event.setStatus("FAILED");
                logger.error("Shared outbox event {} ({}) failed after {} attempts, moved to FAILED: {}",
                        event.getEventId(), event.getEventType(), attempt, event.getLastError());
            } else {
                logger.warn("Shared outbox event {} ({}) publish failed (attempt {}/{}): {}",
                        event.getEventId(), event.getEventType(), attempt, maxRetries, event.getLastError());
            }
            sharedEventOutboxRepository.save(event);
        }
    }
}
