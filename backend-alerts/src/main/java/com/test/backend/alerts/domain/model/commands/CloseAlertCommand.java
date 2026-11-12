package com.test.backend.alerts.domain.model.commands;

public record CloseAlertCommand(Long alertId) {
    public CloseAlertCommand {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId cannot be null");
        }
    }
}
