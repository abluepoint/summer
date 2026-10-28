package com.abluepoint.summer.sample.invitation.infrastructure;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class InvitationAccess {
    private InvitationAccess() {}
    public static void requireSubject(String email, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || email == null || email.isBlank()) throw new AccessDeniedException("Applicant identity does not match");
        if (!com.abluepoint.summer.sample.invitation.domain.InvitationRules.invitedIdentity(email, auth.getName(), jwt.getToken().getClaimAsString("email"), Boolean.TRUE.equals(jwt.getToken().getClaim("email_verified")))) throw new AccessDeniedException("Verified applicant identity does not match");
    }
    public static void requireApplicantSubject(String subject, Authentication auth) {
        if (auth == null || !com.abluepoint.summer.sample.invitation.domain.InvitationRules.applicantIdentity(subject, auth.getName())) throw new AccessDeniedException("Applicant identity does not match");
    }
    public static void requireApplicant(String email, Authentication auth) { if (!(auth instanceof JwtAuthenticationToken)) throw new AccessDeniedException("Applicant authentication required"); requireSubject(email, auth); }
    public static void requireAdmin(String enterpriseId, Authentication auth, String capability) { if (!(auth instanceof JwtAuthenticationToken jwt) || !activeMember(jwt, enterpriseId) || !hasCapability(jwt, enterpriseId, capability)) throw new AccessDeniedException("Enterprise capability required"); }
    private static boolean activeMember(JwtAuthenticationToken jwt, String enterpriseId) { Object c = jwt.getToken().getClaims().get("enterprise_memberships"); return c instanceof Collection<?> col && col.stream().anyMatch(v -> { if (!(v instanceof Map<?, ?> m)) return false; Object enterprise = m.get("enterpriseId"); if (enterprise == null) enterprise = m.get("enterprise_id"); Object status = m.get("status"); if (status == null) status = m.get("membership_status"); return enterpriseId.equals(String.valueOf(enterprise)) && "ACTIVE".equalsIgnoreCase(String.valueOf(status)); }); }
    private static boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String capability) { Object c = jwt.getToken().getClaims().get("enterprise_capabilities"); if (!(c instanceof Map<?, ?> m)) return false; Object values = m.get(enterpriseId); return values instanceof Collection<?> col && col.stream().map(String::valueOf).anyMatch(capability::equals); }
}
