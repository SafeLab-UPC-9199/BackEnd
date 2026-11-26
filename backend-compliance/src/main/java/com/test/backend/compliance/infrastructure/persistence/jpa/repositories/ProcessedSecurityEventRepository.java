package com.test.backend.compliance.infrastructure.persistence.jpa.repositories;

import com.test.backend.compliance.domain.model.entities.ProcessedSecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedSecurityEventRepository extends JpaRepository<ProcessedSecurityEvent, Long> {
    boolean existsByEventId(String eventId);
}
