package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.RoleCreateCommand;

import java.util.List;

public record RoleCreateRequest(String name, String code, List<String> capabilityIds, String bundleId) {
    public com.abluepoint.summer.sample.authorization.application.command.RoleCreateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.RoleCreateCommand(name, code, capabilityIds, bundleId);
    }
}
