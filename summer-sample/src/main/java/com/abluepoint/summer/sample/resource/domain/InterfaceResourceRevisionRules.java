package com.abluepoint.summer.sample.resource.domain;

import java.util.*;

public final class InterfaceResourceRevisionRules {
    private InterfaceResourceRevisionRules() {}

    public static void requireRevision(long revision) {
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
    }

    public static void validateSelection(Long revision, boolean rescan) {
        if (revision != null) requireRevision(revision);
        if (rescan && revision != null) throw new IllegalArgumentException("a fresh scan cannot select a historical revision");
    }

    public static void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
    }

    public static InterfaceResourceDifferenceView difference(long from, long to, InterfaceResourceCatalogView fromRevision, InterfaceResourceCatalogView toRevision) {
        var before = byKey(fromRevision.items());
        var after = byKey(toRevision.items());
        List<InterfaceResourceDescriptor> added = new ArrayList<>(), removed = new ArrayList<>();
        List<InterfaceResourceChangeView> changed = new ArrayList<>();
        before.forEach((key, value) -> {
            var replacement = after.get(key);
            if (replacement == null) removed.add(value);
            else if (!value.equals(replacement)) changed.add(new InterfaceResourceChangeView(value, replacement,
                    value.handler().equals(replacement.handler()) ? "CHANGED" : "REPLACED"));
        });
        after.forEach((key, value) -> { if (!before.containsKey(key)) added.add(value); });
        return new InterfaceResourceDifferenceView(from, to, List.copyOf(added), List.copyOf(removed), List.copyOf(changed));
    }

    private static Map<String, InterfaceResourceDescriptor> byKey(List<InterfaceResourceDescriptor> items) {
        Map<String, InterfaceResourceDescriptor> result = new LinkedHashMap<>();
        items.forEach(item -> result.put(item.key(), item));
        return result;
    }

}
