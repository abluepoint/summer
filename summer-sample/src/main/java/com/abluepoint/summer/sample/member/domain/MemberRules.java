package com.abluepoint.summer.sample.member.domain;

public final class MemberRules {
    private MemberRules() {}
    public static boolean activeMembership(String expectedTenant, String actualTenant, String status) {
        return expectedTenant.equals(actualTenant) && "ACTIVE".equalsIgnoreCase(status);
    }
    public static void validateProvision(String memberId, String subject, String displayName, String email) {
        if (memberId == null || memberId.isBlank() || memberId.length() > 64 || subject == null || subject.isBlank() || subject.length() > 128
                || displayName == null || displayName.isBlank() || displayName.length() > 200 || email == null || email.isBlank() || email.length() > 254)
            throw new IllegalArgumentException("Valid memberId, subject, displayName and email are required");
    }
    public static void requireSameSubject(String existingSubject, String requestedSubject) {
        if (!existingSubject.equals(requestedSubject)) throw new IllegalStateException("Member is bound to a different subject");
    }
    public static void requireVersion(String revision, String expected) {
        if (expected == null || !expected.equals("\"" + revision + "\"")) throw new PreconditionFailedException();
    }
}
