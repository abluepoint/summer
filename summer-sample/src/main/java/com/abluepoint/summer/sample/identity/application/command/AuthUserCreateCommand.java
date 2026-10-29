package com.abluepoint.summer.sample.identity.application.command;

/** Creation input shared with the HTTP request without duplicating its state. */
public interface AuthUserCreateCommand {
    String username();
    String password();
    String role();
}
