package com.abluepoint.summer.sample.identity.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public final class DefaultSessionSnapshotService implements SessionSnapshotService {

    @Override
    public SessionSnapshotView snapshot(Authentication authentication) {
        String subject = authentication.getName();
        String displayName = subject;
        String principalType = "USER";
        if (authentication instanceof JwtAuthenticationToken jwt) {
            displayName = firstClaim(jwt, "name", "preferred_username", subject);
            principalType = firstClaim(jwt, "principal_type", "principalType", principalType);
        }
        Instant expiresAt = expiresAt(authentication);
        List<SessionSnapshotView.EnterpriseMembership> memberships = memberships(authentication);
        List<SessionSnapshotView.Workspace> workspaces = new ArrayList<>();
        workspaces.add(new SessionSnapshotView.Workspace("platform-operations", "platform"));
        if (!memberships.isEmpty()) {
            workspaces.add(new SessionSnapshotView.Workspace("enterprise", "enterprise"));
        }
        String revision = revision(subject, authentication, expiresAt, memberships);
        return new SessionSnapshotView(
                new SessionSnapshotView.Principal(subject, displayName, principalType),
                new SessionSnapshotView.ProductSession("ACTIVE", expiresAt),
                workspaces,
                memberships,
                revision
        );
    }

    private static String firstClaim(JwtAuthenticationToken token, String first, String second, String fallback) {
        Object value = token.getToken().getClaims().get(first);
        if (value == null) {
            value = token.getToken().getClaims().get(second);
        }
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static Instant expiresAt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getExpiresAt() != null) {
            return jwt.getToken().getExpiresAt();
        }
        return Instant.now().plus(15, ChronoUnit.MINUTES);
    }

    private static List<SessionSnapshotView.EnterpriseMembership> memberships(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return List.of();
        }
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(value -> new SessionSnapshotView.EnterpriseMembership(
                        stringValue(value.get("enterpriseId"), value.get("enterprise_id")),
                        stringValue(value.get("displayName"), value.get("display_name")),
                        stringValue(value.get("status"), "ACTIVE")
                ))
                .filter(value -> value.enterpriseId() != null && !value.enterpriseId().isBlank())
                .toList();
    }

    private static String stringValue(Object first, Object second) {
        Object value = first == null ? second : first;
        return value == null ? null : value.toString();
    }

    private static String revision(
            String subject,
            Authentication authentication,
            Instant expiresAt,
            List<SessionSnapshotView.EnterpriseMembership> memberships
    ) {
        String authorities = authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String input = subject + "|" + expiresAt + "|" + authorities + "|" + memberships;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
