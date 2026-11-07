package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Incident;
import com.test.backend.alerts.domain.model.commands.AcknowledgeIncidentCommand;
import com.test.backend.alerts.domain.model.commands.ApplyEscalationCommand;
import com.test.backend.alerts.domain.model.commands.CreateIncidentCommand;
import com.test.backend.alerts.domain.model.commands.ResolveIncidentCommand;
import com.test.backend.alerts.domain.model.entities.CorrectiveAction;
import com.test.backend.alerts.domain.services.DuplicateIncidentPolicy;
import com.test.backend.alerts.domain.services.IncidentCommandService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.IncidentRepository;
import com.test.backend.alerts.infrastructure.security.JWTSecurityContextAdapter;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

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
        var incident = new Incident(
                command.laboratoryId(),
                command.title(),
                command.description(),
                command.severity(),
                command.escalationMaxMins(),
                command.escalationNextRole()
        );
        // QAS-05: denormalize the caller's workspace so tenant-scoped queries
        // (IncidentQueryServiceImpl) can filter without a Laboratory relation.
        jwtSecurityContextAdapter.getCurrentWorkspaceId()
                .ifPresent(incident::setLaboratoryWorkspaceId);

        var savedIncident = incidentRepository.save(incident);

        var correlationId = UUID.randomUUID().toString();
        var outbox = new SharedEventOutbox(
                "INCIDENT_CREATED",
                "Incident",
                String.valueOf(savedIncident.getId()),
                "{\"incidentId\":" + savedIncident.getId() + ",\"laboratoryId\":" + command.laboratoryId() + ",\"severity\":\"" + savedIncident.getSeverity() + "\"}",
                null,
                correlationId,
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        eventOutboxRepository.save(outbox);

        return Optional.of(savedIncident);
    }

    @Override
    public Optional<Incident> handle(AcknowledgeIncidentCommand command) {
        var incidentOpt = incidentRepository.findById(command.incidentId());
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }

        var incident = incidentOpt.get();
        String previousState = incident.getStatus();
        incident.acknowledge();

        var savedIncident = incidentRepository.save(incident);

        var correlationId = UUID.randomUUID().toString();
        var outbox = new SharedEventOutbox(
                "INCIDENT_ACKNOWLEDGED",
                "Incident",
                String.valueOf(savedIncident.getId()),
                "{\"incidentId\":" + savedIncident.getId() + ",\"status\":\"ACKNOWLEDGED\"}",
                null,
                correlationId,
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        eventOutboxRepository.save(outbox);

        return Optional.of(savedIncident);
    }

    @Override
    public Optional<Incident> handle(ResolveIncidentCommand command) {
        var incidentOpt = incidentRepository.findById(command.incidentId());
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }

        var incident = incidentOpt.get();
        String previousState = incident.getStatus();
        var correctiveAction = new CorrectiveAction(incident, command.technicianId(), command.actionTaken());
        incident.resolve(correctiveAction);

        var savedIncident = incidentRepository.save(incident);

        var correlationId = UUID.randomUUID().toString();
        var outbox = new SharedEventOutbox(
                "INCIDENT_RESOLVED",
                "Incident",
                String.valueOf(savedIncident.getId()),
                "{\"incidentId\":" + savedIncident.getId() + ",\"technicianId\":" + command.technicianId() + "}",
                null,
                correlationId,
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        eventOutboxRepository.save(outbox);

        return Optional.of(savedIncident);
    }

    @Override
    public Optional<Incident> handle(ApplyEscalationCommand command) {
        var incidentOpt = incidentRepository.findById(command.incidentId());
        if (incidentOpt.isEmpty()) {
            return Optional.empty();
        }

        var incident = incidentOpt.get();
        String previousState = incident.getStatus();
        incident.escalate(command.nextRole());

        var savedIncident = incidentRepository.save(incident);

        var correlationId = UUID.randomUUID().toString();
        var outbox = new SharedEventOutbox(
                "INCIDENT_ESCALATED",
                "Incident",
                String.valueOf(savedIncident.getId()),
                "{\"incidentId\":" + savedIncident.getId() + ",\"escalatedTo\":\"" + command.nextRole() + "\"}",
                null,
                correlationId,
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        );
        eventOutboxRepository.save(outbox);

        return Optional.of(savedIncident);
    }

    private String calculateHash(Long incidentId, String action, String actor, String previousState, String newState) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = incidentId + ":" + action + ":" + (actor != null ? actor : "system") + ":" + (previousState != null ? previousState : "") + ":" + newState;
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "hash-error";
        }
    }
}
