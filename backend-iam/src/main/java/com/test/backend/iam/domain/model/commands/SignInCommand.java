package com.test.backend.iam.domain.model.commands;

public record SignInCommand(
        String email,
        String password,
        String totpCode,
        String backupCode,
        String ipAddress,
        String userAgent,
        String location
) {
    public SignInCommand(String email, String password) {
        this(email, password, null, null, null, null, null);
    }

    public SignInCommand(String email, String password, String totpCode, String backupCode) {
        this(email, password, totpCode, backupCode, null, null, null);
    }
}
