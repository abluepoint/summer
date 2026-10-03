package com.abluepoint.summer.sample.organization;

class OrganizationConflictException extends RuntimeException {
    OrganizationConflictException(String message) { super(message); }
}

class OrganizationValidationException extends RuntimeException {
    OrganizationValidationException(String message) { super(message); }
}

class OrganizationPreconditionException extends RuntimeException {}

class OrganizationIdempotencyConflictException extends RuntimeException {
    OrganizationIdempotencyConflictException(String message) { super(message); }
}
