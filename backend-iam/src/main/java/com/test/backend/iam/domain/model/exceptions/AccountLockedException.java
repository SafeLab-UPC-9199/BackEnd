package com.test.backend.iam.domain.model.exceptions;

import lombok.Getter;

import java.time.Instant;

@Getter
public class AccountLockedException extends RuntimeException {
    private final Instant lockedUntil;

    public AccountLockedException(String message, Instant lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }
}
