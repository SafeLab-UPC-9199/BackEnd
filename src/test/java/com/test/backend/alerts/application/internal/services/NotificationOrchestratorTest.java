package com.test.backend.alerts.application.internal.services;

import com.test.backend.alerts.domain.services.notification.*;
import com.test.backend.alerts.infrastructure.notification.channels.EmailNotificationChannel;
import com.test.backend.alerts.infrastructure.notification.channels.WebNotificationChannel;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private AuditTrailRepository auditTrailRepository;

    @Mock
    private SharedEventOutboxRepository outboxRepository;

    @Mock
    private ObjectProvider<com.test.backend.iam.application.internal.outboundservices.email.EmailService> emailServiceProvider;

    private WebNotificationChannel webChannel;
    private EmailNotificationChannel emailChannel;
    private NotificationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        webChannel = new WebNotificationChannel();
        emailChannel = new EmailNotificationChannel(emailServiceProvider);
        orchestrator = new NotificationOrchestrator(
                List.of(webChannel, emailChannel),
                auditTrailRepository,
                outboxRepository
        );
    }

    @Test
    @DisplayName("Delivers via primary Web channel when channel is healthy")
    void deliversViaPrimaryWebChannelWhenHealthy() {
        // Arrange
        var message = new NotificationMessage(
                "operator@safelab.com",
                "Temperature Warning in Lab 1",
                "Temperature reached 28.5C",
                "WARNING",
                1L,
                101L,
                "corr-test-1",
                LocalDateTime.now()
        );

        // Act
        NotificationDispatchReport report = orchestrator.dispatchWithFallback(message);

        // Assert
        assertThat(report.isDelivered()).isTrue();
        assertThat(report.primaryResult().channel()).isEqualTo(NotificationChannelType.WEB);
        assertThat(report.primaryResult().success()).isTrue();
        assertThat(report.fallbackTriggered()).isFalse();

        verify(auditTrailRepository, times(1)).save(any(AuditTrail.class));
        verify(outboxRepository, times(1)).save(any(SharedEventOutbox.class));
    }

    @Test
    @DisplayName("QAS-04: Falls back to secondary Email channel within 60s and records 100% of failure when Web fails")
    void fallsBackToSecondaryChannelWithin60sAndRecordsAllFailures() {
        // Arrange - Simulate failure in primary Web channel
        webChannel.setSimulateFailure(true);

        var message = new NotificationMessage(
                "operator@safelab.com",
                "Critical Excursion Alert",
                "Immediate attention required",
                "CRITICAL",
                2L,
                202L,
                "corr-qas04-test",
                LocalDateTime.now()
        );

        // Act
        NotificationDispatchReport report = orchestrator.dispatchWithFallback(message);

        // Assert - QAS-04 acceptance criteria
        assertThat(report.isDelivered()).isTrue();
        assertThat(report.fallbackTriggered()).isTrue();
        assertThat(report.primaryResult().success()).isFalse();
        assertThat(report.primaryResult().channel()).isEqualTo(NotificationChannelType.WEB);
        assertThat(report.fallbackResult().success()).isTrue();
        assertThat(report.fallbackResult().channel()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(report.totalLatencyMs()).isLessThan(60_000L); // QAS-04: delivery in < 60s
        assertThat(report.allFailuresRecorded()).isTrue();

        // Verify 100% of failures are recorded in audit_trails
        ArgumentCaptor<AuditTrail> auditCaptor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(auditTrailRepository, times(2)).save(auditCaptor.capture());

        List<AuditTrail> auditEntries = auditCaptor.getAllValues();
        boolean hasFailureAudit = auditEntries.stream()
                .anyMatch(a -> "NOTIFICATION_CHANNEL_FAILED".equals(a.getAction()) && "FAILED".equals(a.getNewState()));
        boolean hasFallbackAudit = auditEntries.stream()
                .anyMatch(a -> "NOTIFICATION_FALLBACK_DELIVERED".equals(a.getAction()) && "EMAIL".equals(a.getNewState()));

        assertThat(hasFailureAudit).isTrue();
        assertThat(hasFallbackAudit).isTrue();

        // Verify failure and fallback are also published to outbox
        ArgumentCaptor<SharedEventOutbox> outboxCaptor = ArgumentCaptor.forClass(SharedEventOutbox.class);
        verify(outboxRepository, times(2)).save(outboxCaptor.capture());

        List<SharedEventOutbox> outboxEntries = outboxCaptor.getAllValues();
        boolean hasFailureOutbox = outboxEntries.stream()
                .anyMatch(o -> "NOTIFICATION_FAILED".equals(o.getEventType()));
        boolean hasFallbackOutbox = outboxEntries.stream()
                .anyMatch(o -> "NOTIFICATION_FALLBACK_DELIVERED".equals(o.getEventType()));

        assertThat(hasFailureOutbox).isTrue();
        assertThat(hasFallbackOutbox).isTrue();
    }
}
