package com.test.backend.telemetry.infrastructure.persistence.jpa.repositories;

import com.test.backend.telemetry.domain.model.entities.TelemetryIngestionIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelemetryIngestionIdempotencyRepository extends JpaRepository<TelemetryIngestionIdempotency, Long> {
    Optional<TelemetryIngestionIdempotency> findByIdempotencyKey(String idempotencyKey);
}
