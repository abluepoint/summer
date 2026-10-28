package com.abluepoint.summer.sample.authorization.domain.exception;

public final class PolicyValidationException extends RuntimeException {
    public PolicyValidationException(String message) { super(message); }
}
