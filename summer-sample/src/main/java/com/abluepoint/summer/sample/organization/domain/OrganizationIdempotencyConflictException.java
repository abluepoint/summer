package com.abluepoint.summer.sample.organization.domain;

public class OrganizationIdempotencyConflictException extends RuntimeException {
    public OrganizationIdempotencyConflictException(String message) { super(message); }
}
