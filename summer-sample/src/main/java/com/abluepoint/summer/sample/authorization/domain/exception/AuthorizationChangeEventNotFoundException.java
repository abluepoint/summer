package com.abluepoint.summer.sample.authorization.domain.exception;

public class AuthorizationChangeEventNotFoundException extends RuntimeException {
    public AuthorizationChangeEventNotFoundException(String eventId) { super("Authorization change event not found: " + eventId); }
}
