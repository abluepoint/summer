package com.abluepoint.summer.sample.member.application;

import java.util.List;

public record MemberPageView(String enterpriseId, int page, int size, long total, List<MemberSummaryView> items, String revision) {
    public MemberPageView {
        items = List.copyOf(items);
    }
    public int pageSize() { return size; }

    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "MemberPage[" + "enterpriseId=" + enterpriseId + ", " + "page=" + page + ", " + "size=" + size + ", " + "total=" + total + ", " + "items=" + items + ", " + "revision=" + revision + "]"; }
}
