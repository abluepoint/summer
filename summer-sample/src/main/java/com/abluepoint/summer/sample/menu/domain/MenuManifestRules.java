package com.abluepoint.summer.sample.menu.domain;

import java.util.*;
import java.util.function.Function;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestValidationException;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestConflictException;

/** Tree validity, visibility and override precedence independent of authentication and storage. */
public final class MenuManifestRules {
    private MenuManifestRules() {}
    public static boolean visibleAncestors(String parentMenuId, Map<String, String> parents, Set<String> visibleIds) {
        Set<String> visited = new HashSet<>();
        String parent = parentMenuId;
        while (parent != null) {
            if (!visibleIds.contains(parent) || !visited.add(parent)) return false;
            parent = parents.get(parent);
        }
        return true;
    }
    public static boolean customerMenu(String menuId) {
        return Set.of("customer", "customer/drawer", "customer/modal", "customer/page").contains(menuId);
    }
    public static List<String> action(String capability) {
        if (capability == null || capability.isBlank()) return List.of();
        int dot = capability.lastIndexOf('.');
        return dot < 0 || dot == capability.length() - 1 ? List.of("read") : List.of(capability.substring(dot + 1));
    }
    public static void validate(long manifestVersion, List<MenuManifestItem> items) {
        if (manifestVersion < 1 || items == null) throw new MenuManifestValidationException("manifestVersion and items are required");
        Set<String> ids = new HashSet<>();
        for (MenuManifestItem item : items) {
            if (item == null || blank(item.menuId()) || !item.menuId().matches("[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._:-]+)*") || item.menuId().length() > 160) throw new MenuManifestValidationException("Invalid menuId");
            if (!ids.add(item.menuId())) throw new MenuManifestValidationException("Duplicate menuId");
            if (blank(item.path()) || !item.path().startsWith("/") || item.path().contains("://") || item.path().contains("${") || item.path().contains("?")) throw new MenuManifestValidationException("Invalid menu path");
            if (blank(item.titleKey()) || blank(item.iconKey()) || item.sortOrder() == null || item.sortOrder() < 0) throw new MenuManifestValidationException("Invalid menu metadata");
            if (item.capabilityCode() != null && (item.capabilityCode().isBlank() || item.capabilityCode().length() > 200)) throw new MenuManifestValidationException("Invalid capability code");
            if (item.parentMenuId() != null && item.parentMenuId().equals(item.menuId())) throw new MenuManifestValidationException("Menu cannot parent itself");
        }
        for (MenuManifestItem item : items) {
            if (item.parentMenuId() != null && !ids.contains(item.parentMenuId())) throw new MenuManifestValidationException("Unknown parent menu");
            String parent = item.parentMenuId(); Set<String> visited = new HashSet<>();
            while (parent != null) {
                if (!visited.add(parent)) throw new MenuManifestValidationException("Menu parent cycle");
                String currentParent = parent;
                MenuManifestItem parentItem = items.stream().filter(x -> currentParent.equals(x.menuId())).findFirst().orElse(null);
                parent = parentItem == null ? null : parentItem.parentMenuId();
            }
        }
    }
    public static void validateOverride(Integer sortOrder, String parentMenuId, String capabilityCode, String menuId) {
        if (sortOrder != null && sortOrder < 0) throw new MenuManifestValidationException("Invalid sortOrder");
        if (parentMenuId != null && parentMenuId.equals(menuId)) throw new MenuManifestValidationException("Menu cannot parent itself");
        if (capabilityCode != null && (capabilityCode.length() > 200 || capabilityCode.contains("${"))) throw new MenuManifestValidationException("Invalid capability code");
    }
    public static boolean visible(String menuId, List<String> actions) {
        return !customerMenu(menuId) || actions.contains("read");
    }
    public static void requireCurrentVersion(long requested, long current) {
        if (requested < current) throw new MenuManifestConflictException("Manifest version must increase");
    }
    public static void validateOverrideParent(String menuId, String requestedParent, Function<String, String> parentOf) {
        String parent = blankToNull(requestedParent);
        Set<String> visited = new HashSet<>();
        while (parent != null) {
            if (!visited.add(parent) || menuId.equals(parent)) throw new MenuManifestValidationException("Menu parent cycle");
            parent = blankToNull(parentOf.apply(parent));
        }
    }
    public static Effective effective(String parent, int sort, String capability, Boolean enabled,
                                      String overrideParent, Integer overrideSort, String overrideCapability) {
        return new Effective(enabled == null || enabled,
                overrideParent == null ? parent : blankToNull(overrideParent),
                overrideSort == null ? sort : overrideSort,
                overrideCapability == null ? capability : blankToNull(overrideCapability));
    }
    public record Effective(boolean enabled, String parentMenuId, int sortOrder, String capabilityCode) {}
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public static int compare(int leftOrder, String leftId, int rightOrder, String rightId) {
        int order = Integer.compare(leftOrder, rightOrder);
        return order == 0 ? leftId.compareTo(rightId) : order;
    }
}
