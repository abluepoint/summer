package com.abluepoint.summer.sample.authorization.domain.exception;

public final class AuthorizationIdempotencyConflictException extends RuntimeException {
    public AuthorizationIdempotencyConflictException(String message) { super(message); }
}
