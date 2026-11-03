package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.commands.CreateAlertCommand;
import com.test.backend.alerts.domain.model.commands.UpdateAlertCommand;
import com.test.backend.alerts.domain.model.commands.DeleteAlertCommand;
import com.test.backend.alerts.domain.services.AlertCommandService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.infrastructure.persistence.jpa.repositories.AuditTrailRepository;
import com.test.backend.history.domain.model.aggregates.HistoryRecord;
import com.test.backend.history.infrastructure.persistence.jpa.repositories.HistoryRecordRepository;
import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertCommandServiceImpl implements AlertCommandService {

    private final AlertRepository alertRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final com.test.backend.shared.application.CurrentWorkspaceService currentWorkspaceService;
    private final HistoryRecordRepository historyRecordRepository;
    private final AuditTrailRepository auditTrailRepository;

    public AlertCommandServiceImpl(AlertRepository alertRepository,
                                   LaboratoryRepository laboratoryRepository,
                                   com.test.backend.shared.application.CurrentWorkspaceService currentWorkspaceService,
                                   HistoryRecordRepository historyRecordRepository,
                                   AuditTrailRepository auditTrailRepository) {
        this.alertRepository = alertRepository;
        this.laboratoryRepository = laboratoryRepository;
        this.currentWorkspaceService = currentWorkspaceService;
        this.historyRecordRepository = historyRecordRepository;
        this.auditTrailRepository = auditTrailRepository;
    }

    @Override
    @Transactional
    public Optional<Alert> handle(CreateAlertCommand command) {
        Long workspaceId = currentWorkspaceService.getCurrentWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("User does not have an active workspace"));

        Laboratory laboratory = null;
        if (command.laboratoryId() != null) {
            laboratory = laboratoryRepository.findByIdAndWorkspaceId(command.laboratoryId(), workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Laboratory not found in this workspace"));
        }
        var alert = new Alert(command, laboratory);
        alertRepository.save(alert);

        // Save history record
        var record = new HistoryRecord();
        record.setLaboratory(laboratory);
        record.setName(alert.getTitle());
        record.setDescription(alert.getDescription());
        record.setOccurredAt(LocalDateTime.now());
        record.setEventType("incident");
        record.setSeverity(alert.getSeverity());
        record.setStatus("Active");
        historyRecordRepository.save(record);

        return Optional.of(alert);
    }

    @Override
    @Transactional
    public Optional<Alert> handle(UpdateAlertCommand command) {
        Long workspaceId = currentWorkspaceService.getCurrentWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("User does not have an active workspace"));

        var alertOpt = alertRepository.findByIdAndLaboratoryWorkspaceId(command.id(), workspaceId);
        if (alertOpt.isEmpty()) return Optional.empty();

        var alert = alertOpt.get();
        String previousStatus = alert.getStatus();
        boolean wasResolved = "RESOLVED".equalsIgnoreCase(previousStatus);

        Laboratory laboratory = null;
        if (command.laboratoryId() != null) {
            laboratory = laboratoryRepository.findByIdAndWorkspaceId(command.laboratoryId(), workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("Laboratory not found in this workspace"));
        } else if (alert.getLaboratory() != null) {
            laboratory = alert.getLaboratory();
        }

        alert.updateFrom(command, laboratory);
        alertRepository.save(alert);

        boolean isResolvedNow = "RESOLVED".equalsIgnoreCase(alert.getStatus());

        // Audit trail: record status transition with actor from JWT (only when status actually changed)
        String newStatus = alert.getStatus();
        if (previousStatus != null && !previousStatus.equalsIgnoreCase(newStatus)) {
            var actor = currentWorkspaceService.getCurrentUserEmail().orElse("system-automation");
            var audit = new AuditTrail(
                    String.valueOf(alert.getId()),
                    "UPDATE_ALERT_STATUS",
                    actor,
                    previousStatus,
                    newStatus,
                    "Alert '" + alert.getTitle() + "' status changed in laboratory " +
                            (laboratory != null ? laboratory.getName() : alert.getLabName()),
                    calculateHash(alert.getId(), "UPDATE_ALERT_STATUS", actor, previousStatus, newStatus)
            );
            auditTrailRepository.save(audit);
        }

        if (laboratory != null && isResolvedNow) {
            boolean hasOtherActiveAlerts = alertRepository.findByLaboratoryId(laboratory.getId()).stream()
                    .anyMatch(a -> !"RESOLVED".equalsIgnoreCase(a.getStatus()) && !a.getId().equals(alert.getId()));
            if (!hasOtherActiveAlerts) {
                laboratory.setOverallStatus("OPERATIONAL");
                laboratory.setStatus("Operational");
                if (laboratory.getMetrics() != null) {
                    for (var metric : laboratory.getMetrics()) {
                        metric.setStatus("NORMAL");
                    }
                }
                laboratoryRepository.save(laboratory);
            }
        }

        // Log history record if the alert was newly resolved
        if (!wasResolved && isResolvedNow) {
            var record = new HistoryRecord();
            record.setLaboratory(laboratory);
            record.setName(alert.getTitle() + " Resolved");
            record.setDescription("The alert was resolved by " + currentWorkspaceService.getCurrentUserEmail().orElse("System") + ".");
            record.setOccurredAt(LocalDateTime.now());
            record.setEventType("incident");
            record.setSeverity("Info");
            record.setStatus("Resolved");
            historyRecordRepository.save(record);
        }

        return Optional.of(alert);
    }

    @Override
    @Transactional
    public void handle(DeleteAlertCommand command) {
        Long workspaceId = currentWorkspaceService.getCurrentWorkspaceId()
                .orElseThrow(() -> new IllegalStateException("User does not have an active workspace"));

        var alert = alertRepository.findByIdAndLaboratoryWorkspaceId(command.id(), workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found in this workspace"));

        alertRepository.delete(alert);
    }

    /**
     * SHA-256 signature over the transition, mirroring the incident audit trail
     * so both share the same tamper-evident format.
     */
    private String calculateHash(Long entityId, String action, String actor, String previousState, String newState) {
        try {
            var raw = entityId + "|" + action + "|" + actor + "|" + previousState + "|" + newState + "|" + System.currentTimeMillis();
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
