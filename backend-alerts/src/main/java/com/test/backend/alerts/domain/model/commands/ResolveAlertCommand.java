package com.test.backend.alerts.domain.model.commands;

public record ResolveAlertCommand(Long alertId) {
    public ResolveAlertCommand {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId cannot be null");
        }
    }
}
