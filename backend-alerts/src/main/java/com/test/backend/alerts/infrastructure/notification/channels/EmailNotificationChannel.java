package com.test.backend.alerts.infrastructure.notification.channels;

import com.test.backend.alerts.domain.services.notification.NotificationChannel;
import com.test.backend.alerts.domain.services.notification.NotificationChannelType;
import com.test.backend.alerts.domain.services.notification.NotificationMessage;
import com.test.backend.alerts.domain.services.notification.NotificationResult;
import com.test.backend.iam.application.internal.outboundservices.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final ObjectProvider<EmailService> emailServiceProvider;

    public EmailNotificationChannel(ObjectProvider<EmailService> emailServiceProvider) {
        this.emailServiceProvider = emailServiceProvider;
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public NotificationResult send(NotificationMessage message) {
        long start = System.currentTimeMillis();
        try {
            EmailService emailService = emailServiceProvider.getIfAvailable();
            if (emailService != null && message.recipient() != null && message.recipient().contains("@")) {
                emailService.sendAlertEmail(
                        message.recipient(),
                        "Team Member",
                        "Lab #" + message.laboratoryId(),
                        message.subject(),
                        message.body(),
                        message.severity(),
                        "Sensor Unit",
                        "Excursion",
                        "Exceeded Limit"
                );
            }
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
