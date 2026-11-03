package com.test.backend.alerts.application.internal.eventhandlers;

import com.test.backend.alerts.application.internal.commandservices.EvaluateReadingUseCaseImpl;
import com.test.backend.telemetry.domain.model.events.TemperatureReadingRecorded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TemperatureReadingEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureReadingEventHandler.class);

    private final EvaluateReadingUseCaseImpl evaluateReadingUseCase;

    public TemperatureReadingEventHandler(EvaluateReadingUseCaseImpl evaluateReadingUseCase) {
        this.evaluateReadingUseCase = evaluateReadingUseCase;
    }

    @EventListener
    @Transactional
    public void on(TemperatureReadingRecorded event) {
        try {
            evaluateReadingUseCase.execute(event.laboratoryId(), event.metricTypeId(), event.value());
        } catch (Exception ex) {
            logger.warn("Failed to evaluate reading via event for lab {} metric {}: {}",
                    event.laboratoryId(), event.metricTypeId(), ex.getMessage());
        }
    }
}
