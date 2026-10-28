package com.abluepoint.summer.sample.menu.api.request;

import com.abluepoint.summer.sample.menu.application.command.MenuManifestSyncCommand;

import com.abluepoint.summer.sample.menu.domain.MenuManifestItem;

import java.util.List;

public record MenuManifestSyncRequest(long manifestVersion, List<MenuManifestItem> items) {
    public com.abluepoint.summer.sample.menu.application.command.MenuManifestSyncCommand toCommand() {
        return new com.abluepoint.summer.sample.menu.application.command.MenuManifestSyncCommand(manifestVersion, items);
    }
}
