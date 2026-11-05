package com.test.backend.telemetry.application.internal.services;

import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.AutomationRuleRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.SensorConfigurationRepository;
import com.test.backend.automation.infrastructure.persistence.jpa.repositories.UserWorkspaceAccessRepository;
import com.test.backend.history.infrastructure.persistence.jpa.repositories.HistoryRecordRepository;
import com.test.backend.iam.application.internal.outboundservices.email.EmailService;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.telemetry.domain.model.aggregates.MetricType;
import com.test.backend.telemetry.domain.model.aggregates.SensorReading;
import com.test.backend.telemetry.domain.model.events.ReadingRejected;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.MetricTypeRepository;
import com.test.backend.telemetry.infrastructure.persistence.jpa.repositories.SensorReadingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceImplTest {

    @Mock private SensorConfigurationRepository sensorConfigurationRepository;
    @Mock private LaboratoryRepository laboratoryRepository;
    @Mock private SensorReadingRepository sensorReadingRepository;
    @Mock private MetricTypeRepository metricTypeRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private AutomationRuleRepository automationRuleRepository;
    @Mock private HistoryRecordRepository historyRecordRepository;
    @Mock private UserWorkspaceAccessRepository userWorkspaceAccessRepository;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TelemetryIngestionServiceImpl service;

    private SensorConfiguration sensorWithLab() {
        var lab = new Laboratory();
        lab.setName("Bio-Safety Lab 04");
        var sensor = new SensorConfiguration();
        sensor.setSensorName("Sensor A - Bio-Safety Lab 04");
        sensor.setLaboratory(lab);
        return sensor;
    }

    @Test
    @DisplayName("ingest throws for unregistered sensor unit (AAA)")
    void ingest_ShouldThrow_WhenUnitNotRegistered() {
        // Arrange
        when(sensorConfigurationRepository.findByUnit("NOPE")).thenReturn(Optional.empty());

        // Act & Assert
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.ingest("NOPE", "{\"metrics\":{\"temperature\":5.4}}"));
        assertTrue(ex.getMessage().contains("NOPE"));
        verifyNoInteractions(sensorReadingRepository, alertRepository);
    }

    @Test
    @DisplayName("ingest throws for malformed payload (AAA)")
    void ingest_ShouldThrow_WhenPayloadIsInvalidJson() {
        // Arrange
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithLab()));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> service.ingest("Lab-04", "not-json"));
    }

    @Test
    @DisplayName("ingest persists reading with MetricType FK and activates sensor (AAA)")
    void ingest_ShouldSaveReadingAndActivateSensor_WhenMetricKnown() {
        // Arrange
        var sensor = sensorWithLab();
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensor));
        when(metricTypeRepository.findByKey("temperature")).thenReturn(Optional.of(new MetricType()));
        when(sensorReadingRepository.save(any(SensorReading.class))).thenAnswer(i -> i.getArgument(0));
        when(laboratoryRepository.save(any(Laboratory.class))).thenAnswer(i -> i.getArgument(0));
        when(sensorConfigurationRepository.save(any(SensorConfiguration.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        service.ingest("Lab-04", "{\"timestamp\":1712345678900,\"metrics\":{\"temperature\":5.4}}");

        // Assert
        ArgumentCaptor<SensorReading> captor = ArgumentCaptor.forClass(SensorReading.class);
        verify(sensorReadingRepository).save(captor.capture());
        assertEquals(5.4, captor.getValue().getMetricValue());
        assertNotNull(captor.getValue().getMetricType());
        assertEquals("ACTIVE", sensor.getStatus());
        verify(sensorConfigurationRepository).save(sensor);
    }

    @Test
    @DisplayName("ingest ignores unknown metric keys but still activates sensor (AAA)")
    void ingest_ShouldIgnoreUnknownMetricKey_WhenNotInCatalog() {
        // Arrange
        var sensor = sensorWithLab();
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensor));
        when(metricTypeRepository.findByKey("humidity")).thenReturn(Optional.empty());
        when(sensorConfigurationRepository.save(any(SensorConfiguration.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        service.ingest("Lab-04", "{\"metrics\":{\"humidity\":55.0}}");

        // Assert
        verifyNoInteractions(sensorReadingRepository);
        assertEquals("ACTIVE", sensor.getStatus());
    }

    @Test
    @DisplayName("ingest with empty metrics is a no-op (AAA)")
    void ingest_ShouldDoNothing_WhenMetricsEmpty() {
        // Arrange
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithLab()));

        // Act
        service.ingest("Lab-04", "{\"metrics\":{}}");

        // Assert
        verifyNoInteractions(sensorReadingRepository, laboratoryRepository);
        verify(sensorConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingest publishes ReadingRejected for an unregistered unit (AAA)")
    void ingest_ShouldPublishReadingRejected_WhenUnitNotRegistered() {
        // Arrange
        when(sensorConfigurationRepository.findByUnit("NOPE")).thenReturn(Optional.empty());

        // Act
        assertThrows(IllegalArgumentException.class,
                () -> service.ingest("NOPE", "{\"metrics\":{\"temperature\":5.4}}"));

        // Assert
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertInstanceOf(ReadingRejected.class, captor.getValue());
        var rejected = (ReadingRejected) captor.getValue();
        assertEquals("NOPE", rejected.unit());
        assertTrue(rejected.reason().contains("Unregistered"));
        assertNotNull(rejected.occurredAt());
    }

    @Test
    @DisplayName("ingest publishes ReadingRejected when metrics are empty (AAA)")
    void ingest_ShouldPublishReadingRejected_WhenMetricsEmpty() {
        // Arrange
        when(sensorConfigurationRepository.findByUnit("Lab-04")).thenReturn(Optional.of(sensorWithLab()));

        // Act
        service.ingest("Lab-04", "{\"metrics\":{}}");

        // Assert
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertInstanceOf(ReadingRejected.class, captor.getValue());
        assertEquals("Missing or empty metrics", ((ReadingRejected) captor.getValue()).reason());
    }
}
