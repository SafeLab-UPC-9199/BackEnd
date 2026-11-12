package com.test.backend.iam.infrastructure.persistence.jpa.repositories;

import com.test.backend.iam.domain.model.aggregates.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Workspace-scoped directory (QAS-05): fetches users whose emails belong to the workspace. */
    List<User> findByEmailIn(Collection<String> emails);
}
