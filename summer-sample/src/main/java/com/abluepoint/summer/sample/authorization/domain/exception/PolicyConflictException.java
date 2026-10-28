package com.abluepoint.summer.sample.authorization.domain.exception;

public final class PolicyConflictException extends RuntimeException {
    public PolicyConflictException(String message) { super(message); }
}
