package com.abluepoint.summer.sample.authorization.application.command;

import java.util.List;

public record UserRoleAssignmentCommand(List<String> roleIds, List<String> deniedCapabilityCodes) {}
