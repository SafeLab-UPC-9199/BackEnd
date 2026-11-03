package com.test.backend.alerts.interfaces.rest;

import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/alerts/outbox")
public class OutboxAdminController {

    private final SharedEventOutboxRepository sharedEventOutboxRepository;

    public OutboxAdminController(SharedEventOutboxRepository sharedEventOutboxRepository) {
        this.sharedEventOutboxRepository = sharedEventOutboxRepository;
    }

    @GetMapping("/failed")
    public List<Map<String, Object>> failed(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return sharedEventOutboxRepository.findAll().stream()
                .filter(e -> "FAILED".equals(e.getStatus()))
                .limit(limit)
                .map(e -> Map.<String, Object>of(
                        "eventId", e.getEventId(),
                        "eventType", e.getEventType(),
                        "aggregateType", e.getAggregateType(),
                        "aggregateId", e.getAggregateId(),
                        "retryCount", e.getRetryCount(),
                        "lastError", e.getLastError(),
                        "occurredAt", e.getOccurredAt()
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        long pending = sharedEventOutboxRepository.findAll().stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        long published = sharedEventOutboxRepository.findAll().stream().filter(e -> "PUBLISHED".equals(e.getStatus())).count();
        long failed = sharedEventOutboxRepository.findAll().stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        return Map.of("pending", pending, "published", published, "failed", failed);
    }
}
