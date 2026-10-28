package com.abluepoint.summer.sample.menu.application.view;

import java.util.List;

public record MenuManifestView(long manifestVersion, List<MenuManifestItemView> items) {}
