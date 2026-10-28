package com.abluepoint.summer.sample.identity.domain;



public final class SessionReferenceInvalidException extends RuntimeException {
    public SessionReferenceInvalidException() { super("The session reference must be a 64-character hexadecimal value"); }
}
