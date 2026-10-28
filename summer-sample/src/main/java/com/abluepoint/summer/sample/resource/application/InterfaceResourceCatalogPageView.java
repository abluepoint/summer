package com.abluepoint.summer.sample.resource.application;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalogView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDescriptor;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDiscoveryIssue;

import java.util.List;

public record InterfaceResourceCatalogPageView(List<InterfaceResourceDescriptor> items, long revision,
                                    List<InterfaceResourceDiscoveryIssue> issues, boolean complete,
                                    int total, int page, int size) {
    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
    }

    public static InterfaceResourceCatalogPageView of(InterfaceResourceCatalogView view, int page, int size) {
        validate(page, size);
        int start = (int) Math.min((long) page * size, view.items().size());
        int end = (int) Math.min((long) start + size, view.items().size());
        return new InterfaceResourceCatalogPageView(List.copyOf(view.items().subList(start, end)), view.revision(),
                view.issues(), view.complete(), view.items().size(), page, size);
    }
}
