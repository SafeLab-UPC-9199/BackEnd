package com.test.backend.alerts.application.internal.eventhandlers;

import com.test.backend.alerts.domain.model.events.OutboxEventPublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Observes successful outbox deliveries. Records a delivery counter and a
 * structured log line with the full envelope; this is also the extension point
 * where a durable broker publisher (RabbitMQ/Kafka, deferred) will hook in
 * without touching {@code SharedEventOutboxDispatcher}.
 */
@Component
public class OutboxEventPublishedHandler {

    private static final Logger logger = LoggerFactory.getLogger(OutboxEventPublishedHandler.class);

    private final AtomicLong deliveredCount = new AtomicLong();

    @EventListener
    public void on(OutboxEventPublished event) {
        long total = deliveredCount.incrementAndGet();
        logger.info("Outbox event delivered [{}] type={} eventId={} correlationId={} schemaVersion={}",
                total, event.eventType(), event.eventId(), event.correlationId(), event.schemaVersion());
    }

    public long getDeliveredCount() {
        return deliveredCount.get();
    }
}
