package com.abluepoint.summer.sample.member.application;

public record MemberSummaryView(String memberId, String displayName, String email, String status) {
    public String id() { return memberId; }

    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "MemberSummary[" + "memberId=" + memberId + ", " + "displayName=" + displayName + ", " + "email=" + email + ", " + "status=" + status + "]"; }
}
