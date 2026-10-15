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
record InterfaceResourceSnapshot(List<InterfaceResourceDescriptor> items, List<InterfaceResourceDiscoveryIssue> issues) {}

record InterfaceResourceRevisionSummary(long revision, String fingerprint, int resourceCount, String createdAt) {}
record InterfaceResourceChange(InterfaceResourceDescriptor before, InterfaceResourceDescriptor after, String kind) {}
record InterfaceResourceDifference(long from, long to, List<InterfaceResourceDescriptor> added,
                                   List<InterfaceResourceDescriptor> removed, List<InterfaceResourceChange> changed) {}
