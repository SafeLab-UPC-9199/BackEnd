package com.test.backend.iam.interfaces.rest.resources;

public record SignInResource(String email, String password, String totpCode, String backupCode) {
    public SignInResource(String email, String password) {
        this(email, password, null, null);
    }
}
