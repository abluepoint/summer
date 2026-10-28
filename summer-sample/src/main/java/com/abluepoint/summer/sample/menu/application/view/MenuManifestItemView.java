package com.abluepoint.summer.sample.menu.application.view;

import java.util.List;

public record MenuManifestItemView(String menuId, String parentMenuId, String path, String titleKey,
                            String iconKey, int sortOrder, String capabilityCode, boolean enabled,
                            List<String> actions, long version) {}
