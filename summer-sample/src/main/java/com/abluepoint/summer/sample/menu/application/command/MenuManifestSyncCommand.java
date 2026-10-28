package com.abluepoint.summer.sample.menu.application.command;

import com.abluepoint.summer.sample.menu.domain.MenuManifestItem;

import java.util.List;

public record MenuManifestSyncCommand(long manifestVersion, List<MenuManifestItem> items) {}
