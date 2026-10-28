package com.abluepoint.summer.sample.menu.api.request;

import com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand;

public record MenuManifestOverrideRequest(Boolean enabled, String parentMenuId, Integer sortOrder,
                                   String capabilityCode) {
    public com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand toCommand() {
        return new com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand(enabled, parentMenuId, sortOrder, capabilityCode);
    }
}
