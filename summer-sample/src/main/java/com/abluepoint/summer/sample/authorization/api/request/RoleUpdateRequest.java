package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.RoleUpdateCommand;

import java.util.List;

public record RoleUpdateRequest(String name, String code, String status, List<String> capabilityIds, String bundleId) {
    public com.abluepoint.summer.sample.authorization.application.command.RoleUpdateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.RoleUpdateCommand(name, code, status, capabilityIds, bundleId);
    }
}
