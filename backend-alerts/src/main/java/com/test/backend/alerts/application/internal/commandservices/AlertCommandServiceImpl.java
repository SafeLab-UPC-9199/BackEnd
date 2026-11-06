package com.test.backend.alerts.application.internal.commandservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.commands.CreateAlertCommand;
import com.test.backend.alerts.domain.model.commands.UpdateAlertCommand;
import com.test.backend.alerts.domain.model.commands.DeleteAlertCommand;
import com.test.backend.alerts.domain.services.AlertCommandService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AlertCommandServiceImpl implements AlertCommandService {

    private final AlertRepository alertRepository;

    public AlertCommandServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public Optional<Alert> handle(CreateAlertCommand command) {
        var alert = new Alert(command, command.laboratoryId());
        alertRepository.save(alert);
        return Optional.of(alert);
    }

    @Override
    @Transactional
    public Optional<Alert> handle(UpdateAlertCommand command) {
        var alertOpt = alertRepository.findById(command.id());
        if (alertOpt.isEmpty()) return Optional.empty();

        var alert = alertOpt.get();
        alert.updateFrom(command, command.laboratoryId());
        alertRepository.save(alert);
        return Optional.of(alert);
    }

    @Override
    @Transactional
    public void handle(DeleteAlertCommand command) {
        alertRepository.deleteById(command.id());
    }
}
