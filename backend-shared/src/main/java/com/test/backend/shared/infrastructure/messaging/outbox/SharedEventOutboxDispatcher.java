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
            dispatch(event);
        }
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
