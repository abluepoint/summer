package com.abluepoint.summer.sample.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InterfaceResourceCatalog {

    private final List<InterfaceResourceDescriptor> items;

    public InterfaceResourceCatalog(List<InterfaceResourceDescriptor> descriptors) {
        Map<String, InterfaceResourceDescriptor> byKey = new LinkedHashMap<>();
        for (InterfaceResourceDescriptor descriptor : descriptors) {
            InterfaceResourceDescriptor previous = byKey.putIfAbsent(descriptor.key(), descriptor);
            if (previous != null && !previous.equals(descriptor)) {
                if ("ANY /error".equals(descriptor.key())) {
                    continue;
                }
                throw new IllegalStateException("Conflicting interface resource: " + descriptor.key());
            }
        }
        List<InterfaceResourceDescriptor> sorted = new ArrayList<>(byKey.values());
        sorted.sort(Comparator.comparing(InterfaceResourceDescriptor::normalizedPath)
                .thenComparing(InterfaceResourceDescriptor::httpMethod)
                .thenComparing(InterfaceResourceDescriptor::handler));
        this.items = List.copyOf(sorted);
    }

    public List<InterfaceResourceDescriptor> items() {
        return items;
    }

    public long revision() {
        return Integer.toUnsignedLong(items.hashCode());
    }
}
