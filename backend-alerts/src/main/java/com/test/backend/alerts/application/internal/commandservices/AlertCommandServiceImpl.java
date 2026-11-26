package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.commands.*;
import com.test.backend.alerts.domain.services.AlertCommandService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
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
public class AlertCommandServiceImpl implements AlertCommandService {

    private final AlertRepository alertRepository;
    private final SharedEventOutboxRepository eventOutboxRepository;
    private final JWTSecurityContextAdapter jwtSecurityContextAdapter;

    public AlertCommandServiceImpl(AlertRepository alertRepository,
                                   SharedEventOutboxRepository eventOutboxRepository,
                                   JWTSecurityContextAdapter jwtSecurityContextAdapter) {
        this.alertRepository = alertRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.jwtSecurityContextAdapter = jwtSecurityContextAdapter;
    }

    @Override
    public Optional<Alert> handle(CreateAlertCommand command) {
        var alert = new Alert(command, command.laboratoryId());
        jwtSecurityContextAdapter.getCurrentWorkspaceId().ifPresent(alert::setLaboratoryWorkspaceId);
        return Optional.of(alertRepository.save(alert));
    }

    @Override
    public Optional<Alert> handle(UpdateAlertCommand command) {
        var alertOpt = findAccessibleAlert(command.id());
        if (alertOpt.isEmpty()) return Optional.empty();

        var alert = alertOpt.get();
        String previousStatus = alert.getStatus();
        alert.updateFrom(command, command.laboratoryId());
        var saved = alertRepository.save(alert);

        if (previousStatus != null && !previousStatus.equalsIgnoreCase(saved.getStatus())) {
            publishOutbox(saved, "ALERT_STATUS_UPDATED");
        }
        return Optional.of(saved);
    }

    @Override
    public Optional<Alert> handle(AcknowledgeAlertCommand command) {
        return transition(command.alertId(), Alert::acknowledge, "ALERT_ACKNOWLEDGED");
    }

    @Override
    public Optional<Alert> handle(AttendAlertCommand command) {
        return transition(command.alertId(), Alert::attend, "ALERT_ATTENDED");
    }

    @Override
    public Optional<Alert> handle(ResolveAlertCommand command) {
        return transition(command.alertId(), Alert::resolve, "ALERT_RESOLVED");
    }

    @Override
    public Optional<Alert> handle(CloseAlertCommand command) {
        return transition(command.alertId(), Alert::close, "ALERT_CLOSED");
    }

    @Override
    public void handle(DeleteAlertCommand command) {
        var alert = findAccessibleAlert(command.id())
                .orElseThrow(() -> new IllegalArgumentException("Alert not found in this workspace"));
        alertRepository.delete(alert);
    }

    private Optional<Alert> transition(Long alertId, Consumer<Alert> action, String eventType) {
        var alertOpt = findAccessibleAlert(alertId);
        if (alertOpt.isEmpty()) return Optional.empty();

        var alert = alertOpt.get();
        action.accept(alert);
        var saved = alertRepository.save(alert);
        publishOutbox(saved, eventType);
        return Optional.of(saved);
    }

    private Optional<Alert> findAccessibleAlert(Long alertId) {
        var workspaceIdOpt = jwtSecurityContextAdapter.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent() && !jwtSecurityContextAdapter.isCurrentUserAdmin()) {
            return alertRepository.findByIdAndLaboratoryWorkspaceId(alertId, workspaceIdOpt.get());
        }
        return alertRepository.findById(alertId);
    }

    private void publishOutbox(Alert alert, String eventType) {
        String orgId = alert.getLaboratoryWorkspaceId() != null
                ? String.valueOf(alert.getLaboratoryWorkspaceId())
                : null;
        String payload = "{\"alertId\":" + alert.getId()
                + ",\"status\":\"" + alert.getStatus()
                + "\",\"actor\":\"" + jwtSecurityContextAdapter.getCurrentActor() + "\"}";
        eventOutboxRepository.save(new SharedEventOutbox(
                eventType,
                "Alert",
                String.valueOf(alert.getId()),
                payload,
                orgId,
                UUID.randomUUID().toString(),
                SharedEventOutbox.DEFAULT_SCHEMA_VERSION
        ));
    }
}
