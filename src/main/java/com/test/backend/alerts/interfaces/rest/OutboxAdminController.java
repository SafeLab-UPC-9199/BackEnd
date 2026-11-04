package com.test.backend.alerts.interfaces.rest;

import com.test.backend.alerts.application.internal.eventhandlers.OutboxEventPublishedHandler;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read/ops endpoints over the shared transactional outbox, including DLQ
 * replay for FAILED events. Secured by the global authenticated rule.
 */
@RestController
@RequestMapping("/api/v1/alerts/outbox")
public class OutboxAdminController {

    private final SharedEventOutboxRepository sharedEventOutboxRepository;
    private final OutboxEventPublishedHandler outboxEventPublishedHandler;

    public OutboxAdminController(SharedEventOutboxRepository sharedEventOutboxRepository,
                                 OutboxEventPublishedHandler outboxEventPublishedHandler) {
        this.sharedEventOutboxRepository = sharedEventOutboxRepository;
        this.outboxEventPublishedHandler = outboxEventPublishedHandler;
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
                        "lastError", e.getLastError() != null ? e.getLastError() : "",
                        "occurredAt", e.getOccurredAt()
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        long pending = sharedEventOutboxRepository.findAll().stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        long published = sharedEventOutboxRepository.findAll().stream().filter(e -> "PUBLISHED".equals(e.getStatus())).count();
        long failed = sharedEventOutboxRepository.findAll().stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        return Map.of(
                "pending", pending,
                "published", published,
                "failed", failed,
                "deliveredSinceStartup", outboxEventPublishedHandler.getDeliveredCount());
    }

    /**
     * DLQ replay: moves one FAILED event back to PENDING (retry counter and
     * last error are reset) so the dispatcher attempts delivery again.
     */
    @PostMapping("/failed/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFailed(@PathVariable Long id) {
        int requeued = sharedEventOutboxRepository.requeueFailedById(id);
        if (requeued == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().body(Map.of("status", "REQUEUED", "id", id));
    }

    /** DLQ replay-all: re-queues every FAILED event in one operation. */
    @PostMapping("/failed/retry-all")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        int requeued = sharedEventOutboxRepository.requeueAllFailed();
        return ResponseEntity.accepted().body(Map.of("status", "REQUEUED", "requeued", requeued));
    }
}
