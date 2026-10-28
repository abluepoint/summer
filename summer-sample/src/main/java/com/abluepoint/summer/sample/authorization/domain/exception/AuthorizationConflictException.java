package com.abluepoint.summer.sample.authorization.domain.exception;

public final class AuthorizationConflictException extends RuntimeException {
    public AuthorizationConflictException(String message) { super(message); }
}
