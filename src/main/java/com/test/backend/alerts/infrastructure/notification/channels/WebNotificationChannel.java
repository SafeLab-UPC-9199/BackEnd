package com.test.backend.alerts.infrastructure.notification.channels;

import com.test.backend.alerts.domain.services.notification.NotificationChannel;
import com.test.backend.alerts.domain.services.notification.NotificationChannelType;
import com.test.backend.alerts.domain.services.notification.NotificationMessage;
import com.test.backend.alerts.domain.services.notification.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WebNotificationChannel implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(WebNotificationChannel.class);

    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    public void setSimulateFailure(boolean simulate) {
        this.simulateFailure.set(simulate);
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.WEB;
    }

    @Override
    public NotificationResult send(NotificationMessage message) {
        long start = System.currentTimeMillis();
        if (simulateFailure.get()) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("Simulated real-time Web notification failure for message: {}", message.correlationId());
            return NotificationResult.failure(NotificationChannelType.WEB, latency, "Web real-time socket connection timed out or unavailable");
        }
        logger.info("Delivered real-time Web in-app notification to {}: {}", message.recipient(), message.subject());
        long latency = System.currentTimeMillis() - start;
        return NotificationResult.ok(NotificationChannelType.WEB, latency);
    }
}
