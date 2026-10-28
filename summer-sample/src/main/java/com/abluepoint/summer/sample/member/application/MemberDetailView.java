package com.abluepoint.summer.sample.member.application;

public record MemberDetailView(String enterpriseId, String memberId, String displayName, String email, String status, String revision) {
    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "MemberDetail[" + "enterpriseId=" + enterpriseId + ", " + "memberId=" + memberId + ", " + "displayName=" + displayName + ", " + "email=" + email + ", " + "status=" + status + ", " + "revision=" + revision + "]"; }
}
