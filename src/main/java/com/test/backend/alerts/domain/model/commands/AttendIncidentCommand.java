package com.test.backend.alerts.domain.model.commands;

public record AttendIncidentCommand(Long incidentId) {
    public AttendIncidentCommand {
        if (incidentId == null) {
            throw new IllegalArgumentException("incidentId cannot be null");
        }
    }
}
