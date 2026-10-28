package com.abluepoint.summer.sample.application.domain;

public final class ApplicationIdempotencyConflictException extends RuntimeException {
    public ApplicationIdempotencyConflictException(String message) { super(message); }
}
