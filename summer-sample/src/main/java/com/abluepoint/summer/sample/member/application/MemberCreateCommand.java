package com.abluepoint.summer.sample.member.application;

public record MemberCreateCommand(
        String subject,
        String displayName,
        String email) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "MemberCreateRequest[subject=" + subject + ", displayName=" + displayName + ", email=" + email + "]";
    }
}
