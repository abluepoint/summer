package com.abluepoint.summer.sample.authorization.domain.exception;

public final class AuthorizationValidationException extends RuntimeException {
    public AuthorizationValidationException(String message) { super(message); }
}
