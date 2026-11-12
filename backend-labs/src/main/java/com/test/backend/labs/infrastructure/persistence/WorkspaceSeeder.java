package com.test.backend.labs.infrastructure.persistence;

import com.test.backend.labs.domain.model.aggregates.Workspace;
import com.test.backend.labs.infrastructure.persistence.jpa.repositories.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the default workspace (Fase 6, ADR-006).
 *
 * <p>{@code Laboratory.workspace} is {@code nullable=false} and nothing in the
 * microservices topology created rows, so {@code POST /api/v1/laboratories}
 * always failed with "Column 'workspace_id' cannot be null". This runner
 * guarantees the base workspace exists; laboratory creation resolves the
 * caller's tenant (gateway header) or falls back to it.</p>
 */
@Component
@Order(1)
public class WorkspaceSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceSeeder.class);

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceSeeder(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public void run(String... args) {
        if (workspaceRepository.findByCode(Workspace.BASE_CODE).isPresent()) {
            logger.info("Base workspace already seeded. Skipping.");
            return;
        }

        workspaceRepository.save(new Workspace(Workspace.BASE_NAME, Workspace.BASE_CODE));
        logger.info("Seeded base workspace '{}' (code={}).", Workspace.BASE_NAME, Workspace.BASE_CODE);
    }
}
