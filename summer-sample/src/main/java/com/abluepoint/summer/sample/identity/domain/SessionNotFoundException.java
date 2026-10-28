package com.abluepoint.summer.sample.identity.domain;



public final class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException() { super("The session was not found"); }
}
