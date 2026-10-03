package com.abluepoint.summer.sample.enterprise;

import java.util.List;

record PlatformEnterpriseView(
        String enterpriseId,
        String name,
        String did,
        String license,
        String status,
        String qualification,
        String resources,
        String activity,
        String revision) {}

record PlatformEnterpriseSummary(
        long total,
        long authenticated,
        long pending,
        long abnormal) {}

record PlatformEnterprisePage(
        List<PlatformEnterpriseView> items,
        int page,
        int pageSize,
        long total,
        PlatformEnterpriseSummary summary) {
    PlatformEnterprisePage {
        items = List.copyOf(items);
    }
}
