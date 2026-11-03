package com.test.backend.compliance.interfaces.rest;

import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.interfaces.rest.resources.AuditTrailResource;
import com.test.backend.compliance.interfaces.rest.transform.AuditTrailResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Audit Trails", description = "Read-only audit log of state transitions (incidents and alerts)")
@RestController
@RequestMapping("/api/v1/audit-trails")
public class AuditTrailController {

    private final AuditTrailQueryService auditTrailQueryService;

    public AuditTrailController(AuditTrailQueryService auditTrailQueryService) {
        this.auditTrailQueryService = auditTrailQueryService;
    }

    @GetMapping
    @Operation(
            summary = "Get audit trail entries",
            description = "Returns state transitions (who, when, previous vs new state). " +
                    "Optional filters: targetId (incident/alert id) and action. " +
                    "Non-admin users only receive entries from their own workspace."
    )
    public ResponseEntity<List<AuditTrailResource>> getAuditTrails(
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String action
    ) {
        var query = new GetAuditTrailsQuery(targetId, action);
        var trails = auditTrailQueryService.handle(query);
        var resources = trails.stream()
                .map(AuditTrailResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
