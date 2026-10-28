package com.abluepoint.summer.sample.enterprise.application;

import java.util.List;

public record PlatformEnterprisePageView(
        List<PlatformEnterpriseView> items,
        int page,
        int pageSize,
        long total,
        PlatformEnterpriseSummaryView summary) {
    public PlatformEnterprisePageView {
        items = List.copyOf(items);
    }

    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "PlatformEnterprisePage[" + "items=" + items + ", " + "page=" + page + ", " + "pageSize=" + pageSize + ", " + "total=" + total + ", " + "summary=" + summary + "]"; }
}
