package com.abluepoint.summer.sample.invitation.domain;

public class InvitationIdempotencyConflictException extends RuntimeException {
    public InvitationIdempotencyConflictException(String message) { super(message); }
}

