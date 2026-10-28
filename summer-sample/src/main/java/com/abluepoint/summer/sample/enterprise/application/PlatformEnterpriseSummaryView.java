package com.abluepoint.summer.sample.enterprise.application;

import java.util.List;

public record PlatformEnterpriseSummaryView(
        long total,
        long authenticated,
        long pending,
        long abnormal) {
    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "PlatformEnterpriseSummary[" + "total=" + total + ", " + "authenticated=" + authenticated + ", " + "pending=" + pending + ", " + "abnormal=" + abnormal + "]"; }
}

