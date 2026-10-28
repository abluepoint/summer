package com.abluepoint.summer.sample.resource.domain;

import java.util.List;

public record InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision,
                                    List<InterfaceResourceDiscoveryIssue> issues, boolean complete) {
    public InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision) { this(items, revision, List.of()); }
    public InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision, List<InterfaceResourceDiscoveryIssue> issues) {
        this(List.copyOf(items), revision, List.copyOf(issues), issues.isEmpty());
    }
}
