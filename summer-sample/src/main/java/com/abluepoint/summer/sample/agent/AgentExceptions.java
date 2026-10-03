package com.abluepoint.summer.sample.agent;

final class AgentValidationException extends RuntimeException {
    AgentValidationException(String message) { super(message); }
}

final class AgentConflictException extends RuntimeException {
    AgentConflictException(String message) { super(message); }
}

final class AgentPreconditionException extends RuntimeException {
    AgentPreconditionException(String message) { super(message); }
}

final class AgentIdempotencyConflictException extends RuntimeException {
    AgentIdempotencyConflictException(String message) { super(message); }
}
