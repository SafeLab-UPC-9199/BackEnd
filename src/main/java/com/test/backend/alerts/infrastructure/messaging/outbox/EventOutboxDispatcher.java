package com.test.backend.alerts.infrastructure.messaging.outbox;

import com.test.backend.alerts.domain.model.entities.EventOutbox;
import com.test.backend.alerts.domain.model.events.OutboxEventPublished;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the transactional outbox for PENDING events and republishes them
 * through the application event bus (Iteration 0 dispatcher).
 *
 * Delivery guarantees: at-least-once. An event that fails to publish is
 * retried up to {@link #MAX_RETRIES} times; after that it is marked FAILED
 * (logical dead-letter) and kept for inspection instead of blocking the queue.
 */
@Component
public class EventOutboxDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(EventOutboxDispatcher.class);

    /** Logical dead-letter threshold: after this many failed attempts the event stops being retried. */
    static final int MAX_RETRIES = 5;

    private final EventOutboxRepository eventOutboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public EventOutboxDispatcher(EventOutboxRepository eventOutboxRepository,
                                 ApplicationEventPublisher applicationEventPublisher) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Polls pending outbox entries. Interval is configurable via
     * {@code outbox.dispatch-interval-ms} (default 5s).
     */
    @Scheduled(fixedDelayString = "${outbox.dispatch-interval-ms:5000}")
    public void dispatchPendingEvents() {
        var pendingEvents = eventOutboxRepository.findByStatus("PENDING");
        for (var event : pendingEvents) {
            dispatch(event);
        }
    }

    private void dispatch(EventOutbox event) {
        try {
            applicationEventPublisher.publishEvent(new OutboxEventPublished(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getPayload(),
                    event.getCorrelationId(),
                    event.getSchemaVersion(),
                    event.getOrganizationId(),
                    event.getOccurredAt()
            ));
            event.setStatus("PUBLISHED");
            event.setLastError(null);
            eventOutboxRepository.save(event);
        } catch (Exception ex) {
            int attempt = event.getRetryCount() + 1;
            event.setRetryCount(attempt);
            event.setLastError(truncate(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            if (attempt >= MAX_RETRIES) {
                event.setStatus("FAILED");
                logger.error("Outbox event {} ({}) failed after {} attempts, moved to FAILED: {}",
                        event.getEventId(), event.getEventType(), attempt, event.getLastError());
            } else {
                logger.warn("Outbox event {} ({}) publish failed (attempt {}/{}): {}",
                        event.getEventId(), event.getEventType(), attempt, MAX_RETRIES, event.getLastError());
            }
            eventOutboxRepository.save(event);
        }
    }

    private String truncate(String message) {
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
