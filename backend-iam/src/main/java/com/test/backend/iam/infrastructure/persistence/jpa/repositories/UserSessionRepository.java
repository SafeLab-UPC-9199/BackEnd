package com.test.backend.iam.infrastructure.persistence.jpa.repositories;

import com.test.backend.iam.domain.model.entities.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionId(String sessionId);
    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastActivityAtDesc(Long userId, Instant now);
}
