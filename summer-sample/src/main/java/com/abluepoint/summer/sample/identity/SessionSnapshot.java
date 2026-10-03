package com.abluepoint.summer.sample.identity;

import java.time.Instant;
import java.util.List;

/**
 * Protocol-safe product bootstrap contract. OAuth credentials and capability
 * details are deliberately not part of this representation.
 */
public record SessionSnapshot(
        Principal principal,
        ProductSession productSession,
        List<Workspace> workspaces,
        List<EnterpriseMembership> enterpriseMemberships,
        String revision
) {
    public SessionSnapshot {
        workspaces = List.copyOf(workspaces);
        enterpriseMemberships = List.copyOf(enterpriseMemberships);
    }

    public record Principal(String subject, String displayName, String principalType) {
    }

    public record ProductSession(String status, Instant expiresAt) {
    }

    public record Workspace(String code, String kind) {
    }

    public record EnterpriseMembership(String enterpriseId, String displayName, String status) {
    }
}
