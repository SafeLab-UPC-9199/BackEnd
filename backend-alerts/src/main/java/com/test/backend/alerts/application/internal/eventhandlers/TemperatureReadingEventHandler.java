package com.test.backend.alerts.application.internal.eventhandlers;

import com.test.backend.alerts.domain.services.EvaluateReadingUseCase;
import com.test.backend.telemetry.domain.model.events.TemperatureReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens to readings accepted by the canonical ingestion pipeline and runs the
 * thermal excursion evaluation, so telemetry and alerts stay decoupled
 * (Iteration 1).
 */
@Component
public class TemperatureReadingEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureReadingEventHandler.class);

    /** Only this metric can produce a thermal excursion incident. */
    private static final String THERMAL_METRIC_KEY = "temperature";

    private final EvaluateReadingUseCase evaluateReadingUseCase;

    public TemperatureReadingEventHandler(EvaluateReadingUseCase evaluateReadingUseCase) {
        this.evaluateReadingUseCase = evaluateReadingUseCase;
    }

    @EventListener
    @Transactional
    public void on(TemperatureReadingRecorded event) {
        try {
            if (event.metricKey() == null || !THERMAL_METRIC_KEY.equalsIgnoreCase(event.metricKey())) {
                return;
            }
            evaluateReadingUseCase.evaluate(
                    event.laboratoryId(),
                    event.value(),
                    event.minThreshold(),
                    event.maxThreshold(),
                    event.labName()
            );
        } catch (Exception ex) {
            logger.warn("Failed to evaluate reading via event for lab {} metric {}: {}",
                    event.laboratoryId(), event.metricTypeId(), ex.getMessage());
        }
    }
}
