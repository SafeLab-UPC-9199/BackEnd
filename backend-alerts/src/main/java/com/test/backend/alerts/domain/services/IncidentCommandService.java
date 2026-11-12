package com.test.backend.alerts.domain.services;

import com.test.backend.alerts.domain.model.aggregates.Incident;
import com.test.backend.alerts.domain.model.commands.AcknowledgeIncidentCommand;
import com.test.backend.alerts.domain.model.commands.ApplyEscalationCommand;
import com.test.backend.alerts.domain.model.commands.AttendIncidentCommand;
import com.test.backend.alerts.domain.model.commands.CloseIncidentCommand;
import com.test.backend.alerts.domain.model.commands.CreateIncidentCommand;
import com.test.backend.alerts.domain.model.commands.ResolveIncidentCommand;

import java.util.Optional;

public interface IncidentCommandService {
    Optional<Incident> handle(CreateIncidentCommand command);
    Optional<Incident> handle(AcknowledgeIncidentCommand command);
    Optional<Incident> handle(AttendIncidentCommand command);
    Optional<Incident> handle(ResolveIncidentCommand command);
    Optional<Incident> handle(CloseIncidentCommand command);
    Optional<Incident> handle(ApplyEscalationCommand command);
}
