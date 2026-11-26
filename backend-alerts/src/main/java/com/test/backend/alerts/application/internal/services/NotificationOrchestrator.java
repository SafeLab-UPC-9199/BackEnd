package com.test.backend.alerts.application.internal.services;

import com.test.backend.alerts.domain.services.notification.*;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);
    private static final long MAX_ALLOWED_LATENCY_MS = 60_000L;

    private final Map<NotificationChannelType, NotificationChannel> channels;
    private final SharedEventOutboxRepository outboxRepository;

    public NotificationOrchestrator(
            List<NotificationChannel> channelList,
            SharedEventOutboxRepository outboxRepository
    ) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotificationChannel::getChannelType, c -> c));
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public NotificationDispatchReport dispatchWithFallback(NotificationMessage message) {
        long startTime = System.currentTimeMillis();

        NotificationChannel primaryChannel = channels.get(NotificationChannelType.WEB);
        if (primaryChannel == null) {
            throw new IllegalStateException("Primary channel WEB is not configured");
        }

        NotificationResult primaryResult = primaryChannel.send(message);
        if (primaryResult.success()) {
            recordSuccess(message, primaryResult, false);
            return new NotificationDispatchReport(primaryResult, null, false, System.currentTimeMillis() - startTime, true);
        }

        recordFailure(message, primaryResult);

        NotificationChannel fallbackChannel = channels.get(NotificationChannelType.EMAIL);
        if (fallbackChannel == null) {
            fallbackChannel = channels.get(NotificationChannelType.SMS);
        }
        if (fallbackChannel == null) {
            return new NotificationDispatchReport(primaryResult, null, true, System.currentTimeMillis() - startTime, true);
        }

        NotificationResult fallbackResult = fallbackChannel.send(message);
        long totalLatency = System.currentTimeMillis() - startTime;

        if (fallbackResult.success()) {
            recordSuccess(message, fallbackResult, true);
            if (totalLatency > MAX_ALLOWED_LATENCY_MS) {
                logger.warn("QAS-04 WARNING: Fallback delivery took {}ms, exceeding 60s limit!", totalLatency);
            }
        } else {
            recordFailure(message, fallbackResult);
        }

        return new NotificationDispatchReport(primaryResult, fallbackResult, true, totalLatency, true);
    }

    private void recordFailure(NotificationMessage message, NotificationResult result) {
        String targetId = targetId(message);
        outboxRepository.save(new SharedEventOutbox(
                "NOTIFICATION_FAILED",
                "Notification",
                targetId,
                "{\"channel\":\"" + result.channel().name() + "\",\"error\":\"" + result.errorMessage()
                        + "\",\"correlationId\":\"" + message.correlationId() + "\"}",
                null,
                message.correlationId(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        ));
    }

    private void recordSuccess(NotificationMessage message, NotificationResult result, boolean isFallback) {
        String action = isFallback ? "NOTIFICATION_FALLBACK_DELIVERED" : "NOTIFICATION_DELIVERED";
        outboxRepository.save(new SharedEventOutbox(
                action,
                "Notification",
                targetId(message),
                "{\"channel\":\"" + result.channel().name() + "\",\"latencyMs\":" + result.latencyMs()
                        + ",\"correlationId\":\"" + message.correlationId() + "\"}",
                null,
                message.correlationId(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        ));
    }

    private String targetId(NotificationMessage message) {
        return message.incidentId() != null
                ? String.valueOf(message.incidentId())
                : String.valueOf(message.laboratoryId());
    }
}
