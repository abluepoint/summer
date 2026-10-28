package com.abluepoint.summer.sample.identity.domain;

/** Stored account facts used by authentication and provisioning, independent of persistence. */
public record AuthAccount(String username, String passwordHash, String role, boolean active) {
    public boolean availableForAuthentication() { return active; }
}
