package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.UserRoleAssignmentCommand;

import java.util.List;

public record UserRoleAssignmentRequest(List<String> roleIds, List<String> deniedCapabilityCodes) {
    public com.abluepoint.summer.sample.authorization.application.command.UserRoleAssignmentCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.UserRoleAssignmentCommand(roleIds, deniedCapabilityCodes);
    }
}
