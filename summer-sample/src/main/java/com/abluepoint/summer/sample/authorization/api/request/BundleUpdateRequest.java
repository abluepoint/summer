package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.BundleUpdateCommand;

import java.util.List;

public record BundleUpdateRequest(String name, String code, String status, List<String> capabilityIds, String changeSummary) {
    public com.abluepoint.summer.sample.authorization.application.command.BundleUpdateCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.BundleUpdateCommand(name, code, status, capabilityIds, changeSummary);
    }
}
