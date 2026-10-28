package com.abluepoint.summer.sample.authorization.application.command;

public record CapabilityCreateCommand(String code, String resource, String action, String riskLevel, String scope) {}
