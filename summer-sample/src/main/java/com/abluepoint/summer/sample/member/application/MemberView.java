package com.abluepoint.summer.sample.member.application;

@org.springframework.modulith.NamedInterface("directory")
public record MemberView(String enterpriseId, String memberId, String subject, String displayName, String email, String status, long version) {
    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "Member[" + "enterpriseId=" + enterpriseId + ", " + "memberId=" + memberId + ", " + "subject=" + subject + ", " + "displayName=" + displayName + ", " + "email=" + email + ", " + "status=" + status + ", " + "version=" + version + "]"; }
}
