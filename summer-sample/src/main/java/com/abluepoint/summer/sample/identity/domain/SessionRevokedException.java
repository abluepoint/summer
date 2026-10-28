package com.abluepoint.summer.sample.identity.domain;



public final class SessionRevokedException extends RuntimeException {
    public SessionRevokedException() { super("The server session has been revoked"); }
}
