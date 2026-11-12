package com.test.backend.alerts.domain.model.commands;

public record CloseIncidentCommand(Long incidentId) {
    public CloseIncidentCommand {
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId cannot be null");
        }
    }
}
