package com.test.backend.alerts.interfaces.rest;

import com.test.backend.alerts.application.internal.eventhandlers.OutboxEventPublishedHandler;
import com.test.backend.shared.domain.model.entities.SharedEventOutbox;
import com.test.backend.shared.infrastructure.persistence.jpa.repositories.SharedEventOutboxRepository;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import org.springframework.http.HttpStatus;
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
 * replay for FAILED events.
 *
 * <p>QAS-05 (mirrors the monolith): listing endpoints are filtered by the
 * caller's workspace (organizationId) unless the caller is an Administrator,
 * and the DLQ replay endpoints are Administrator-only. Identity comes from
 * the gateway headers read by {@link RequestIdentity} (ADR-006).</p>
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
    public ResponseEntity<List<Map<String, Object>>> failed(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (!canManageOutbox()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var failedEvents = sharedEventOutboxRepository.findAll().stream()
                .filter(this::visibleToCaller)
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
        return ResponseEntity.ok(failedEvents);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        if (!canManageOutbox()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var visible = sharedEventOutboxRepository.findAll().stream()
                .filter(this::visibleToCaller)
                .toList();
        long pending = visible.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        long published = visible.stream().filter(e -> "PUBLISHED".equals(e.getStatus())).count();
        long failed = visible.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "published", published,
                "failed", failed,
                "deliveredSinceStartup", outboxEventPublishedHandler.getDeliveredCount()));
    }

    @PostMapping("/failed/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFailed(@PathVariable Long id) {
        // QAS-05: DLQ replay mutates shared state - Administrator only.
        if (!RequestIdentity.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int requeued = sharedEventOutboxRepository.requeueFailedById(id);
        if (requeued == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().body(Map.of("status", "REQUEUED", "id", id));
    }

    @PostMapping("/failed/retry-all")
    public ResponseEntity<Map<String, Object>> retryAllFailed() {
        // QAS-05: DLQ replay mutates shared state - Administrator only.
        if (!RequestIdentity.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int requeued = sharedEventOutboxRepository.requeueAllFailed();
        return ResponseEntity.accepted().body(Map.of("status", "REQUEUED", "requeued", requeued));
    }

    /** True when the caller may read outbox entries (admin = global, otherwise workspace-filtered). */
    private boolean canManageOutbox() {
        return RequestIdentity.isAdmin() || RequestIdentity.currentWorkspaceId().isPresent();
    }

    /** QAS-05 filter: non-administrators only see events of their own workspace. */
    private boolean visibleToCaller(SharedEventOutbox event) {
        if (RequestIdentity.isAdmin()) {
            return true;
        }
        var workspaceIdOpt = RequestIdentity.currentWorkspaceId();
        if (workspaceIdOpt.isEmpty()) {
            return false;
        }
        return String.valueOf(workspaceIdOpt.get()).equals(event.getOrganizationId());
    }
}
