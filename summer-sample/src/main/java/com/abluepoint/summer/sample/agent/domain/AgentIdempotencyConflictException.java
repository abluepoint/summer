package com.abluepoint.summer.sample.agent.domain;

public final class AgentIdempotencyConflictException extends RuntimeException {
    public AgentIdempotencyConflictException(String message) { super(message); }
}
