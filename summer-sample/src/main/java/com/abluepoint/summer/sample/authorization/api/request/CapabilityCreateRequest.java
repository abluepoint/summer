package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.CapabilityCreateCommand;

public record CapabilityCreateRequest(String code, String resource, String action, String riskLevel, String scope) {
    public com.abluepoint.summer.sample.authorization.application.command.CapabilityCreateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.CapabilityCreateCommand(code, resource, action, riskLevel, scope);
    }
}
