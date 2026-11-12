package com.test.backend.alerts.application.internal.services;

import com.test.backend.alerts.domain.services.notification.*;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates multi-channel notification dispatch with automatic fallback (QAS-04):
 * When real-time Web notification fails for an active alert/incident, the failure
 * is recorded (100% auditable) and delivery falls back to a secondary channel (Email/SMS)
 * in under 60 seconds.
 */
@Service
public class NotificationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestrator.class);
    private static final long MAX_ALLOWED_LATENCY_MS = 60_000L; // QAS-04: < 60s

    private final Map<NotificationChannelType, NotificationChannel> channels;
    private final AuditTrailRepository auditTrailRepository;
    private final SharedEventOutboxRepository outboxRepository;

    public NotificationOrchestrator(
            List<NotificationChannel> channelList,
            AuditTrailRepository auditTrailRepository,
            SharedEventOutboxRepository outboxRepository
    ) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotificationChannel::getChannelType, c -> c));
        this.auditTrailRepository = auditTrailRepository;
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
            long totalLatency = System.currentTimeMillis() - startTime;
            return new NotificationDispatchReport(primaryResult, null, false, totalLatency, true);
        }

        // QAS-04: Primary failed -> Record 100% of failures in audit trail and outbox
        recordFailure(message, primaryResult);

        // Fallback to secondary channel (EMAIL or SMS)
        NotificationChannel fallbackChannel = channels.get(NotificationChannelType.EMAIL);
        if (fallbackChannel == null) {
            fallbackChannel = channels.get(NotificationChannelType.SMS);
        }

        if (fallbackChannel == null) {
            long totalLatency = System.currentTimeMillis() - startTime;
            return new NotificationDispatchReport(primaryResult, null, true, totalLatency, true);
        }

        logger.info("QAS-04: Triggering secondary fallback channel [{}] for message {}",
                fallbackChannel.getChannelType(), message.correlationId());

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
        String targetId = message.incidentId() != null
                ? String.valueOf(message.incidentId())
                : String.valueOf(message.laboratoryId());

        String signature = calculateHash(targetId, "NOTIFICATION_CHANNEL_FAILED", "NotificationOrchestrator",
                result.channel().name(), "FAILED");

        var audit = new AuditTrail(
                targetId,
                "NOTIFICATION_CHANNEL_FAILED",
                "NotificationOrchestrator",
                result.channel().name(),
                "FAILED",
                "Channel " + result.channel().name() + " failed: " + result.errorMessage(),
                signature
        );
        auditTrailRepository.save(audit);

        var outbox = new SharedEventOutbox(
                "NOTIFICATION_FAILED",
                "Notification",
                targetId,
                "{\"channel\":\"" + result.channel().name() + "\",\"error\":\"" + result.errorMessage() + "\",\"correlationId\":\"" + message.correlationId() + "\"}",
                null,
                message.correlationId(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        outboxRepository.save(outbox);

        logger.warn("100% Failure Logged: Notification failed on channel [{}] for target [{}]: {}",
                result.channel(), targetId, result.errorMessage());
    }

    private void recordSuccess(NotificationMessage message, NotificationResult result, boolean isFallback) {
        String targetId = message.incidentId() != null
                ? String.valueOf(message.incidentId())
                : String.valueOf(message.laboratoryId());

        String action = isFallback ? "NOTIFICATION_FALLBACK_DELIVERED" : "NOTIFICATION_DELIVERED";
        String signature = calculateHash(targetId, action, "NotificationOrchestrator", null, result.channel().name());

        var audit = new AuditTrail(
                targetId,
                action,
                "NotificationOrchestrator",
                null,
                result.channel().name(),
                (isFallback ? "Fallback delivery" : "Primary delivery") + " succeeded via " + result.channel().name(),
                signature
        );
        auditTrailRepository.save(audit);

        var outbox = new SharedEventOutbox(
                action,
                "Notification",
                targetId,
                "{\"channel\":\"" + result.channel().name() + "\",\"latencyMs\":" + result.latencyMs() + ",\"correlationId\":\"" + message.correlationId() + "\"}",
                null,
                message.correlationId(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        outboxRepository.save(outbox);
    }

    private String calculateHash(String targetId, String action, String actor, String previousState, String newState) {
        try {
            var raw = targetId + "|" + action + "|" + actor + "|" + previousState + "|" + newState + "|" + System.currentTimeMillis();
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
