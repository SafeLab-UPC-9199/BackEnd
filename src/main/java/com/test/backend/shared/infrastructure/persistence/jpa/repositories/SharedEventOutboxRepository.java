package com.test.backend.shared.infrastructure.persistence.jpa.repositories;

import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SharedEventOutboxRepository extends JpaRepository<SharedEventOutbox, Long> {
    List<SharedEventOutbox> findByStatus(String status);

    @Modifying
    @Query("delete from SharedEventOutbox e where e.status = 'PUBLISHED' and e.occurredAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
