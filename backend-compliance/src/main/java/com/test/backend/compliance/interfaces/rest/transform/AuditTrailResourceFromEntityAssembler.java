package com.test.backend.compliance.interfaces.rest.transform;

import com.test.backend.compliance.domain.model.aggregates.AuditTrail;
import com.test.backend.compliance.interfaces.rest.resources.AuditTrailResource;

public class AuditTrailResourceFromEntityAssembler {
    public static AuditTrailResource toResourceFromEntity(AuditTrail entity) {
        return new AuditTrailResource(
                entity.getId(),
                entity.getTargetId(),
                entity.getTargetType(),
                entity.getAction(),
                entity.getActor(),
                entity.getPreviousState(),
                entity.getNewState(),
                entity.getOrganizationId(),
                entity.getIpAddress(),
                entity.getDetails(),
                entity.getSignature(),
                entity.getCreatedAt()
        );
    }
}
