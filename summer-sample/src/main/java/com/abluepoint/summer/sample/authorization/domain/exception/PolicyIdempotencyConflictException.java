package com.abluepoint.summer.sample.authorization.domain.exception;

public final class PolicyIdempotencyConflictException extends RuntimeException {
    public PolicyIdempotencyConflictException(String message) { super(message); }
}
