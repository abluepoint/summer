package com.abluepoint.summer.sample.identity.domain;

public final class SessionIndexUnavailableException extends RuntimeException {
    public SessionIndexUnavailableException() { super("Shared session storage is unavailable"); }
}
