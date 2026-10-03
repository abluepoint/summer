package com.abluepoint.summer.sample.authorization.policy;

final class PolicyValidationException extends RuntimeException {
    PolicyValidationException(String message) { super(message); }
}

final class PolicyConflictException extends RuntimeException {
    PolicyConflictException(String message) { super(message); }
}

final class PolicyPreconditionException extends RuntimeException {
    PolicyPreconditionException(String message) { super(message); }
}

final class PolicyIdempotencyConflictException extends RuntimeException {
    PolicyIdempotencyConflictException(String message) { super(message); }
}
