package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.RoleCapabilitiesCommand;

import java.util.List;

public record RoleCapabilitiesRequest(List<String> capabilityIds) {
    public com.abluepoint.summer.sample.authorization.application.command.RoleCapabilitiesCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.RoleCapabilitiesCommand(capabilityIds);
    }
}
