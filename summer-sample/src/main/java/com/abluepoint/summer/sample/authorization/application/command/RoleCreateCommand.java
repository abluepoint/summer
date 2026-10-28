package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

public record RoleCreateCommand(String name, String code, List<String> capabilityIds, String bundleId) {}
