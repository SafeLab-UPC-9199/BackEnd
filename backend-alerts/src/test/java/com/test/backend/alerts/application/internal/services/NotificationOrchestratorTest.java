package com.test.backend.alerts.application.internal.services;

import com.test.backend.alerts.domain.services.notification.*;
import com.test.backend.alerts.infrastructure.notification.channels.EmailNotificationChannel;
import com.test.backend.alerts.infrastructure.notification.channels.WebNotificationChannel;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private SharedEventOutboxRepository outboxRepository;

    private WebNotificationChannel webChannel;
    private EmailNotificationChannel emailChannel;
    private NotificationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        webChannel = new WebNotificationChannel();
        emailChannel = new EmailNotificationChannel();
        orchestrator = new NotificationOrchestrator(
                List.of(webChannel, emailChannel),
                outboxRepository
        );
    }

    @Test
    @DisplayName("Delivers via primary Web channel when channel is healthy")
    void deliversViaPrimaryWebChannelWhenHealthy() {
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

        NotificationDispatchReport report = orchestrator.dispatchWithFallback(message);

        assertThat(report.isDelivered()).isTrue();
        assertThat(report.primaryResult().channel()).isEqualTo(NotificationChannelType.WEB);
        assertThat(report.primaryResult().success()).isTrue();
        assertThat(report.fallbackTriggered()).isFalse();

        verify(outboxRepository, times(1)).save(any(SharedEventOutbox.class));
    }

    @Test
    @DisplayName("QAS-04: Falls back to secondary Email channel within 60s and records failure in outbox when Web fails")
    void fallsBackToSecondaryChannelWithin60sAndRecordsAllFailures() {
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

        NotificationDispatchReport report = orchestrator.dispatchWithFallback(message);

        assertThat(report.isDelivered()).isTrue();
        assertThat(report.fallbackTriggered()).isTrue();
        assertThat(report.primaryResult().success()).isFalse();
        assertThat(report.primaryResult().channel()).isEqualTo(NotificationChannelType.WEB);
        assertThat(report.fallbackResult().success()).isTrue();
        assertThat(report.fallbackResult().channel()).isEqualTo(NotificationChannelType.EMAIL);
        assertThat(report.totalLatencyMs()).isLessThan(60_000L);
        assertThat(report.allFailuresRecorded()).isTrue();

        ArgumentCaptor<SharedEventOutbox> outboxCaptor = ArgumentCaptor.forClass(SharedEventOutbox.class);
        verify(outboxRepository, times(2)).save(outboxCaptor.capture());

        List<SharedEventOutbox> outboxEntries = outboxCaptor.getAllValues();
        assertThat(outboxEntries.stream().anyMatch(o -> "NOTIFICATION_FAILED".equals(o.getEventType()))).isTrue();
        assertThat(outboxEntries.stream().anyMatch(o -> "NOTIFICATION_FALLBACK_DELIVERED".equals(o.getEventType()))).isTrue();
    }
}
