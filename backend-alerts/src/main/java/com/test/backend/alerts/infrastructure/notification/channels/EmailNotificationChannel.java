package com.test.backend.alerts.infrastructure.notification.channels;

import com.test.backend.alerts.domain.services.notification.NotificationChannel;
import com.test.backend.alerts.domain.services.notification.NotificationChannelType;
import com.test.backend.alerts.domain.services.notification.NotificationMessage;
import com.test.backend.alerts.domain.services.notification.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public NotificationResult send(NotificationMessage message) {
        long start = System.currentTimeMillis();
        try {
            logger.info("Delivered secondary fallback Email notification to {}: {}", message.recipient(), message.subject());
            long latency = System.currentTimeMillis() - start;
            return NotificationResult.ok(NotificationChannelType.EMAIL, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("Email channel delivery failed: {}", e.getMessage());
            return NotificationResult.failure(NotificationChannelType.EMAIL, latency, e.getMessage());
        }
    }
}
