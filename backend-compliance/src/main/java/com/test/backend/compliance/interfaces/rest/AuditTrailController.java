package com.test.backend.compliance.interfaces.rest;

import com.test.backend.compliance.domain.model.queries.GetAuditTrailsQuery;
import com.test.backend.compliance.domain.services.AuditTrailQueryService;
import com.test.backend.compliance.interfaces.rest.resources.AuditTrailResource;
import com.test.backend.compliance.interfaces.rest.transform.AuditTrailResourceFromEntityAssembler;
import com.test.backend.shared.infrastructure.security.RequestIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Audit Trails", description = "Read-only audit log of state transitions and security events")
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
            description = "Returns audit entries filtered by targetId, action, user, organizationId, and date range (from/to). " +
                    "Non-admin users are restricted to their own workspace."
    )
    public ResponseEntity<List<AuditTrailResource>> getAuditTrails(
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        Long effectiveOrgId = organizationId;
        if (!RequestIdentity.isAdmin()) {
            var workspaceId = RequestIdentity.currentWorkspaceId();
            if (workspaceId.isEmpty() || (organizationId != null && !organizationId.equals(workspaceId.get()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            effectiveOrgId = workspaceId.get();
        }

        var query = new GetAuditTrailsQuery(targetId, action, user, effectiveOrgId, from, to);
        var resources = auditTrailQueryService.handle(query).stream()
                .map(AuditTrailResourceFromEntityAssembler::toResourceFromEntity)
                .toList();
        return ResponseEntity.ok(resources);
    }
}
