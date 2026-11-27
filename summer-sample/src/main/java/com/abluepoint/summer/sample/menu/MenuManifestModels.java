package com.abluepoint.summer.sample.menu;

import java.util.List;

record MenuManifestSyncRequest(long manifestVersion, List<MenuManifestItemRequest> items) {}
record MenuManifestItemRequest(String menuId, String parentMenuId, String path, String titleKey,
                               String iconKey, Integer sortOrder, String capabilityCode, String clientCode) {}
record MenuManifestItemView(String menuId, String parentMenuId, String path, String titleKey,
                            String iconKey, int sortOrder, String capabilityCode, boolean enabled,
                            List<String> actions, long version, String clientCode) {}
record MenuManifestView(long manifestVersion, List<MenuManifestItemView> items,
    List<com.abluepoint.summer.sample.dictionary.PlatformDictionaryOptions.Option> clientOptions) {
    MenuManifestView(long version, List<MenuManifestItemView> items) { this(version, items, List.of()); }
}
