package com.test.backend.alerts.domain.model.commands;

public record AttendAlertCommand(Long alertId) {
    public AttendAlertCommand {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId cannot be null");
        }
    }
}
