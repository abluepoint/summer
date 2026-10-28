package com.abluepoint.summer.sample.resource;

import java.util.List;

record InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision,
                                    List<InterfaceResourceDiscoveryIssue> issues, boolean complete) {
    InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision) { this(items, revision, List.of()); }
    InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision, List<InterfaceResourceDiscoveryIssue> issues) {
        this(List.copyOf(items), revision, List.copyOf(issues), issues.isEmpty());
    }
}
record InterfaceResourceDiscoveryIssue(String contextId, String source, String code, String detail) {}
record InterfaceResourceCatalogPage(List<InterfaceResourceDescriptor> items, long revision,
                                    List<InterfaceResourceDiscoveryIssue> issues, boolean complete,
                                    int total, int page, int size) {
    static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
    }

    static InterfaceResourceCatalogPage of(InterfaceResourceCatalogView view, int page, int size) {
        validate(page, size);
        int start = (int) Math.min((long) page * size, view.items().size());
        int end = (int) Math.min((long) start + size, view.items().size());
        return new InterfaceResourceCatalogPage(List.copyOf(view.items().subList(start, end)), view.revision(),
                view.issues(), view.complete(), view.items().size(), page, size);
    }
}
record InterfaceResourceSnapshot(List<InterfaceResourceDescriptor> items, List<InterfaceResourceDiscoveryIssue> issues) {}

record InterfaceResourceRevisionSummary(long revision, String fingerprint, int resourceCount, String createdAt) {}
record InterfaceResourceChange(InterfaceResourceDescriptor before, InterfaceResourceDescriptor after, String kind) {}
record InterfaceResourceDifference(long from, long to, List<InterfaceResourceDescriptor> added,
                                   List<InterfaceResourceDescriptor> removed, List<InterfaceResourceChange> changed) {}
