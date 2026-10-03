package com.abluepoint.summer.sample.member;

import java.util.List;

record MemberPage(String enterpriseId, int page, int size, long total, List<MemberSummary> items, String revision) {
    MemberPage {
        items = List.copyOf(items);
    }
    public int pageSize() { return size; }
}
