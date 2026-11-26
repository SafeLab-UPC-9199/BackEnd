package com.test.backend.iam.interfaces.rest.transform;

import com.test.backend.iam.domain.model.commands.SignInCommand;
import com.test.backend.iam.interfaces.rest.resources.SignInResource;

public class SignInCommandFromResourceAssembler {
    public static SignInCommand toCommandFromResource(SignInResource resource) {
        return new SignInCommand(resource.email(), resource.password(), resource.totpCode(), resource.backupCode());
    }

    public static SignInCommand toCommandFromResource(SignInResource resource, String ipAddress, String userAgent, String location) {
        return new SignInCommand(
                resource.email(),
                resource.password(),
                resource.totpCode(),
                resource.backupCode(),
                ipAddress,
                userAgent,
                location
        );
    }
}
