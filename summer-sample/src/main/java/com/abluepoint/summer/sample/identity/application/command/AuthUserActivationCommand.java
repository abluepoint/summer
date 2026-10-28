package com.abluepoint.summer.sample.identity.application.command;

public record AuthUserActivationCommand(String username, boolean active) {}
