package com.abluepoint.summer.sample.customer.model.command;
public record CustomerUpdateCommand(boolean emailPresent, String email, boolean displayNamePresent, String displayName, boolean activePresent, boolean active) {}
