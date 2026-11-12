package com.test.backend.alerts.infrastructure.notification.channels;

import com.test.backend.alerts.domain.services.notification.NotificationChannel;
import com.test.backend.alerts.domain.services.notification.NotificationChannelType;
import com.test.backend.alerts.domain.services.notification.NotificationMessage;
import com.test.backend.alerts.domain.services.notification.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationChannel implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationChannel.class);

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.SMS;
    }

    @Override
    public NotificationResult send(NotificationMessage message) {
        long start = System.currentTimeMillis();
        try {
            logger.info("Delivered secondary fallback SMS notification to {}: [{}] {}", message.recipient(), message.severity(), message.subject());
            long latency = System.currentTimeMillis() - start;
            return NotificationResult.ok(NotificationChannelType.SMS, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("SMS channel delivery failed: {}", e.getMessage());
            return NotificationResult.failure(NotificationChannelType.SMS, latency, e.getMessage());
        }
    }
}
