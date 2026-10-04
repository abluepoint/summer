package com.abluepoint.summer.sample.menu;

import java.util.List;

record MenuManifestSyncRequest(long manifestVersion, List<MenuManifestItemRequest> items) {}
record MenuManifestItemRequest(String menuId, String parentMenuId, String path, String titleKey,
                               String iconKey, Integer sortOrder, String capabilityCode) {}
record MenuManifestOverrideRequest(Boolean enabled, String parentMenuId, Integer sortOrder,
                                   String capabilityCode) {}
record MenuManifestItemView(String menuId, String parentMenuId, String path, String titleKey,
                            String iconKey, int sortOrder, String capabilityCode, boolean enabled,
                            List<String> actions, long version) {}
record MenuManifestView(long manifestVersion, List<MenuManifestItemView> items) {}
