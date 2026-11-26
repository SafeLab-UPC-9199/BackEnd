package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Incident;
import com.test.backend.alerts.domain.model.commands.*;
import com.test.backend.alerts.domain.model.entities.CorrectiveAction;
import com.test.backend.alerts.domain.services.DuplicateIncidentPolicy;
import com.test.backend.alerts.domain.services.IncidentCommandService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.IncidentRepository;
import com.test.backend.alerts.infrastructure.security.JWTSecurityContextAdapter;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@Transactional
public class IncidentCommandServiceImpl implements IncidentCommandService {

    private final IncidentRepository incidentRepository;
    private final SharedEventOutboxRepository eventOutboxRepository;
    private final DuplicateIncidentPolicy duplicateIncidentPolicy;
    private final JWTSecurityContextAdapter jwtSecurityContextAdapter;

    public IncidentCommandServiceImpl(
            IncidentRepository incidentRepository,
            SharedEventOutboxRepository eventOutboxRepository,
            DuplicateIncidentPolicy duplicateIncidentPolicy,
            JWTSecurityContextAdapter jwtSecurityContextAdapter
    ) {
        this.incidentRepository = incidentRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.duplicateIncidentPolicy = duplicateIncidentPolicy;
        this.jwtSecurityContextAdapter = jwtSecurityContextAdapter;
    }

    @Override
    public Optional<Incident> handle(CreateIncidentCommand command) {
        if (command.laboratoryId() == null) {
            throw new IllegalArgumentException("Laboratory ID is required");
        }
        if (duplicateIncidentPolicy.hasActiveIncident(command.laboratoryId())) {
            throw new IllegalStateException("An active incident already exists for laboratory ID " + command.laboratoryId());
        }

        var incident = new Incident(
                command.laboratoryId(),
                command.title(),
                command.description(),
                command.severity(),
                command.escalationMaxMins(),
                command.escalationNextRole()
        );
        jwtSecurityContextAdapter.getCurrentWorkspaceId().ifPresent(incident::setLaboratoryWorkspaceId);

        var saved = incidentRepository.save(incident);
        publishOutbox(saved, "INCIDENT_CREATED",
                "{\"incidentId\":" + saved.getId() + ",\"laboratoryId\":" + saved.getLaboratoryId()
                        + ",\"severity\":\"" + saved.getSeverity() + "\"}");
        return Optional.of(saved);
    }

    @Override
    public Optional<Incident> handle(AcknowledgeIncidentCommand command) {
        return transition(command.incidentId(), Incident::acknowledge, "INCIDENT_ACKNOWLEDGED",
                inc -> "{\"incidentId\":" + inc.getId() + ",\"status\":\"ACKNOWLEDGED\"}");
    }

    @Override
    public Optional<Incident> handle(AttendIncidentCommand command) {
        return transition(command.incidentId(), Incident::attend, "INCIDENT_ATTENDED",
                inc -> "{\"incidentId\":" + inc.getId() + ",\"status\":\"IN_PROGRESS\"}");
    }

    @Override
    public Optional<Incident> handle(CloseIncidentCommand command) {
        return transition(command.incidentId(), Incident::close, "INCIDENT_CLOSED",
                inc -> "{\"incidentId\":" + inc.getId() + ",\"status\":\"CLOSED\"}");
    }

    @Override
    public Optional<Incident> handle(ResolveIncidentCommand command) {
        return transition(command.incidentId(),
                inc -> inc.resolve(new CorrectiveAction(inc, command.technicianId(), command.actionTaken())),
                "INCIDENT_RESOLVED",
                inc -> "{\"incidentId\":" + inc.getId() + ",\"technicianId\":" + command.technicianId() + "}");
    }

    @Override
    public Optional<Incident> handle(ApplyEscalationCommand command) {
        return transition(command.incidentId(),
                inc -> inc.escalate(command.nextRole()),
                "INCIDENT_ESCALATED",
                inc -> "{\"incidentId\":" + inc.getId() + ",\"escalatedTo\":\"" + inc.getEscalationNextRole() + "\"}");
    }

    private Optional<Incident> transition(Long incidentId, Consumer<Incident> action,
                                          String eventType, java.util.function.Function<Incident, String> payloadFn) {
        var incidentOpt = findAccessibleIncident(incidentId);
        if (incidentOpt.isEmpty()) return Optional.empty();

        var incident = incidentOpt.get();
        action.accept(incident);
        var saved = incidentRepository.save(incident);
        publishOutbox(saved, eventType, payloadFn.apply(saved));
        return Optional.of(saved);
    }

    private Optional<Incident> findAccessibleIncident(Long incidentId) {
        var workspaceIdOpt = jwtSecurityContextAdapter.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent() && !jwtSecurityContextAdapter.isCurrentUserAdmin()) {
            return incidentRepository.findByIdAndLaboratoryWorkspaceId(incidentId, workspaceIdOpt.get());
        }
        return incidentRepository.findById(incidentId);
    }

    private void publishOutbox(Incident incident, String eventType, String payload) {
        String orgId = incident.getLaboratoryWorkspaceId() != null
                ? String.valueOf(incident.getLaboratoryWorkspaceId())
                : null;
        eventOutboxRepository.save(new SharedEventOutbox(
                eventType,
                "Incident",
                String.valueOf(incident.getId()),
                payload,
                orgId,
                UUID.randomUUID().toString(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        ));
    }
}
