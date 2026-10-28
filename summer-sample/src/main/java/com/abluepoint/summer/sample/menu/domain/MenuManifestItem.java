package com.abluepoint.summer.sample.menu.domain;

public record MenuManifestItem(String menuId, String parentMenuId, String path, String titleKey,
                               String iconKey, Integer sortOrder, String capabilityCode) {}
