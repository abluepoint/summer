package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

public record RoleCapabilitiesCommand(List<String> capabilityIds) {}
