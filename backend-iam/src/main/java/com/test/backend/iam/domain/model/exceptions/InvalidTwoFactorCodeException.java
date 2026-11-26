package com.test.backend.iam.domain.model.exceptions;

public class InvalidTwoFactorCodeException extends RuntimeException {
    public InvalidTwoFactorCodeException(String message) {
        super(message);
    }
}
