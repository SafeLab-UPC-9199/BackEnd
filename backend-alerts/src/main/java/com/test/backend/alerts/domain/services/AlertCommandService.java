package com.test.backend.alerts.domain.services;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.commands.*;

import java.util.Optional;

public interface AlertCommandService {
    Optional<Alert> handle(CreateAlertCommand command);
    Optional<Alert> handle(UpdateAlertCommand command);
    Optional<Alert> handle(AcknowledgeAlertCommand command);
    Optional<Alert> handle(AttendAlertCommand command);
    Optional<Alert> handle(ResolveAlertCommand command);
    Optional<Alert> handle(CloseAlertCommand command);
    void handle(DeleteAlertCommand command);
}
