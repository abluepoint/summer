package com.abluepoint.summer.sample.identity.application.command;

public record AuthUserCreateCommand(String username, String password, String role) {}
