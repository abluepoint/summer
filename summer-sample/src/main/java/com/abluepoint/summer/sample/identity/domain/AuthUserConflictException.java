package com.abluepoint.summer.sample.identity.domain;


public class AuthUserConflictException extends RuntimeException {
    public AuthUserConflictException() { super("Username already exists"); }
}
