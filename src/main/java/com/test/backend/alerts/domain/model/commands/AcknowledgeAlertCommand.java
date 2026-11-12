package com.test.backend.alerts.domain.model.commands;

public record AcknowledgeAlertCommand(Long alertId) {
    public AcknowledgeAlertCommand {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId cannot be null");
        }
    }
}
