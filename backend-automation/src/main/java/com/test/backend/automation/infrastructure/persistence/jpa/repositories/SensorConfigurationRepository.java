package com.test.backend.automation.infrastructure.persistence.jpa.repositories;

import com.test.backend.automation.domain.model.aggregates.SensorConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SensorConfigurationRepository extends JpaRepository<SensorConfiguration, Long> {
    Optional<SensorConfiguration> findBySensorName(String sensorName);
    Optional<SensorConfiguration> findByUnit(String unit);
    // Workspace-scoped variants (QAS-05) removed in the microservices split:
    // SensorConfiguration stores a flat laboratoryId and module code filters by it.
}
