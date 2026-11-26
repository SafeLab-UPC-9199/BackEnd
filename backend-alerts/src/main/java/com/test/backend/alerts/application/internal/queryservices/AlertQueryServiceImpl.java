package com.test.backend.alerts.application.internal.queryservices;

import com.test.backend.alerts.domain.model.aggregates.Alert;
import com.test.backend.alerts.domain.model.queries.GetAlertByIdQuery;
import com.test.backend.alerts.domain.model.queries.GetAllAlertsQuery;
import com.test.backend.alerts.domain.services.AlertQueryService;
import com.test.backend.alerts.infrastructure.persistence.jpa.repositories.AlertRepository;
import com.test.backend.alerts.infrastructure.security.JWTSecurityContextAdapter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AlertQueryServiceImpl implements AlertQueryService {

    private final AlertRepository alertRepository;
    private final JWTSecurityContextAdapter jwtSecurityContextAdapter;

    public AlertQueryServiceImpl(AlertRepository alertRepository,
                                 JWTSecurityContextAdapter jwtSecurityContextAdapter) {
        this.alertRepository = alertRepository;
        this.jwtSecurityContextAdapter = jwtSecurityContextAdapter;
    }

    @Override
    public List<Alert> handle(GetAllAlertsQuery query) {
        var workspaceIdOpt = jwtSecurityContextAdapter.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent() && !jwtSecurityContextAdapter.isCurrentUserAdmin()) {
            return alertRepository.findByLaboratoryWorkspaceId(workspaceIdOpt.get());
        }
        return alertRepository.findAll();
    }

    @Override
    public Optional<Alert> handle(GetAlertByIdQuery query) {
        var workspaceIdOpt = jwtSecurityContextAdapter.getCurrentWorkspaceId();
        if (workspaceIdOpt.isPresent() && !jwtSecurityContextAdapter.isCurrentUserAdmin()) {
            return alertRepository.findByIdAndLaboratoryWorkspaceId(query.id(), workspaceIdOpt.get());
        }
        return alertRepository.findById(query.id());
    }
}
