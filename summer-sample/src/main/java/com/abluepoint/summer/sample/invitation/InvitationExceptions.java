package com.abluepoint.summer.sample.invitation;

class InvitationConflictException extends RuntimeException {
    InvitationConflictException(String message) { super(message); }
}

class InvitationContextMismatchException extends RuntimeException {
    InvitationContextMismatchException(String message) { super(message); }
}

class InvitationValidationException extends RuntimeException {
    InvitationValidationException(String message) { super(message); }
}

class InvitationPreconditionException extends RuntimeException {}

class InvitationIdempotencyConflictException extends RuntimeException {
    InvitationIdempotencyConflictException(String message) { super(message); }
}

class JoinApplicationConflictException extends RuntimeException {
    JoinApplicationConflictException(String message) { super(message); }
}
