package com.abluepoint.summer.sample.authorization;

final class AuthorizationValidationException extends RuntimeException {
    AuthorizationValidationException(String message) { super(message); }
}

final class AuthorizationConflictException extends RuntimeException {
    AuthorizationConflictException(String message) { super(message); }
}

final class AuthorizationPreconditionException extends RuntimeException {
    AuthorizationPreconditionException(String message) { super(message); }
}

final class AuthorizationIdempotencyConflictException extends RuntimeException {
    AuthorizationIdempotencyConflictException(String message) { super(message); }
}
