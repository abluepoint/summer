package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.BundleCreateCommand;

import java.util.List;

public record BundleCreateRequest(String name, String code, List<String> capabilityIds, String changeSummary) {
    public com.abluepoint.summer.sample.authorization.application.command.BundleCreateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.BundleCreateCommand(name, code, capabilityIds, changeSummary);
    }
}
