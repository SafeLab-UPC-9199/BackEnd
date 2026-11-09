package com.test.backend.labs.application.internal.commandservices;

import com.test.backend.labs.domain.model.aggregates.Laboratory;
import com.test.backend.labs.domain.model.aggregates.Workspace;
import com.test.backend.labs.domain.model.commands.CreateLaboratoryCommand;
import com.test.backend.labs.domain.model.commands.UpdateLaboratoryCommand;
import com.test.backend.labs.domain.model.commands.DeleteLaboratoryCommand;
import com.test.backend.labs.domain.model.entities.LabMetricSubscription;
import com.test.backend.labs.domain.services.LaboratoryCommandService;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.LaboratoryRepository;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.WorkspaceRepository;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LaboratoryCommandServiceImpl implements LaboratoryCommandService {

    private final LaboratoryRepository laboratoryRepository;
    private final WorkspaceRepository workspaceRepository;

    public LaboratoryCommandServiceImpl(LaboratoryRepository laboratoryRepository,
                                        WorkspaceRepository workspaceRepository) {
        this.laboratoryRepository = laboratoryRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional
    public Optional<Laboratory> handle(CreateLaboratoryCommand command) {
        if (laboratoryRepository.findByLabCode(command.labCode()).isPresent()) {
            throw new IllegalArgumentException("Laboratory with code " + command.labCode() + " already exists");
        }

        var laboratory = new Laboratory(command);
        // workspace_id is nullable=false: resolve the caller's tenant from the
        // gateway-injected header (ADR-006), falling back to the seeded base
        // workspace when the token carries no workspaceId claim.
        laboratory.setWorkspace(resolveWorkspace());

        // Create metric subscriptions
        if (command.metricSubscriptions() != null) {
            for (var subData : command.metricSubscriptions()) {
                var sub = new LabMetricSubscription();
                sub.setMetricTypeId(subData.metricTypeId());
                sub.setMinThreshold(subData.minThreshold());
                sub.setMaxThreshold(subData.maxThreshold());
                sub.setActive(true);
                laboratory.getMetricSubscriptions().add(sub);
            }
        }

        return Optional.of(laboratoryRepository.save(laboratory));
    }

    @Override
    @Transactional
    public Optional<Laboratory> handle(UpdateLaboratoryCommand command) {
        var labOpt = laboratoryRepository.findById(command.id());
        if (labOpt.isEmpty()) return Optional.empty();

        var lab = labOpt.get();
        lab.setName(command.name());
        lab.setType(command.type());
        lab.setStatus(command.status());
        lab.setBuilding(command.building());
        lab.setFloor(command.floor());
        lab.setLabCode(command.labCode());
        lab.setRoomNumber(command.roomNumber());
        lab.setDescription(command.description());
        lab.setOverallStatus(command.overallStatus());
        lab.setActive(command.active());
        lab.setIsLive(command.isLive());
        lab.setNextMaintenance(command.nextMaintenance());
        lab.setMaintenanceDaysLeft(command.maintenanceDaysLeft());

        return Optional.of(laboratoryRepository.save(lab));
    }

    @Override
    @Transactional
    public void handle(DeleteLaboratoryCommand command) {
        laboratoryRepository.deleteById(command.id());
    }

    /**
     * Tenant resolution for new laboratories: the workspace reported by the
     * gateway header when present, otherwise the seeded base workspace. A
     * header pointing at a workspace that does not exist is an error, not a
     * silent fallback.
     */
    private Workspace resolveWorkspace() {
        var workspaceId = RequestIdentity.currentWorkspaceId();
        if (workspaceId.isPresent()) {
            return workspaceRepository.findById(workspaceId.get())
                    .orElseThrow(() -> new IllegalStateException(
                            "Workspace not found for id " + workspaceId.get()));
        }
        return workspaceRepository.findByCode(Workspace.BASE_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "No workspace available: base workspace has not been seeded"));
    }
}
