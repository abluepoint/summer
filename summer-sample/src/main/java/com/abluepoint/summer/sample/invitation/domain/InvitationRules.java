package com.abluepoint.summer.sample.invitation.domain;

import java.time.Instant;

/** Invitation consumption and join-review lifecycle constraints. */
public final class InvitationRules {
    private InvitationRules() {}
    public static boolean expired(Instant expiresAt, Instant now) { return expiresAt.isBefore(now); }
    public static void requirePending(String state, String message) {
        if (!"PENDING".equals(state)) throw new InvitationConflictException(message);
    }
    public static void requireEditable(String state, String message) {
        if (!"PENDING".equals(state) && !"NEEDS_INFO".equals(state)) throw new JoinApplicationConflictException(message);
    }
    public static boolean invitedIdentity(String email, String subject, String claimedEmail, boolean verified) {
        return email != null && !email.isBlank() && (email.equalsIgnoreCase(subject) || (verified && email.equalsIgnoreCase(claimedEmail)));
    }
    public static boolean applicantIdentity(String applicantSubject, String subject) {
        return applicantSubject != null && applicantSubject.equals(subject);
    }
}
