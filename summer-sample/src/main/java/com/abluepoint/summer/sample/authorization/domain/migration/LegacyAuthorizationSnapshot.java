package com.abluepoint.summer.sample.authorization.domain.migration;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, trusted export. Resource identities come from exact catalog matching, never a
 * wildcard.
 */
public record LegacyAuthorizationSnapshot(
    String snapshotId,
    String domainKind,
    String domainId,
    long revision,
    List<Capability> capabilities,
    List<Role> roles,
    List<User> users,
    List<Resource> resources,
    List<Bundle> bundles,
    List<String> unresolved) {
  public LegacyAuthorizationSnapshot {
    capabilities = List.copyOf(capabilities);
    roles = List.copyOf(roles);
    users = List.copyOf(users);
    resources = List.copyOf(resources);
    bundles = List.copyOf(bundles);
    unresolved = List.copyOf(unresolved);
  }

  public record Capability(
      String id, String code, String resource, boolean active, Set<String> managementDomains) {
    public Capability {
      managementDomains =
          managementDomains == null
              ? Set.of()
              : Collections.unmodifiableSet(new TreeSet<>(managementDomains));
    }

    public Capability(String id, String code, String resource, boolean active) {
      this(id, code, resource, active, Set.of());
    }
  }

  public record Role(String id, boolean active, Set<String> capabilityIds, String bundleId) {
    public Role {
      capabilityIds = Collections.unmodifiableSet(new TreeSet<>(capabilityIds));
    }
  }

  public record User(
      String subject,
      boolean active,
      Set<String> roleIds,
      Set<String> deniedCodes,
      String constraintFingerprint) {
    public User {
      roleIds = Collections.unmodifiableSet(new TreeSet<>(roleIds));
      deniedCodes = Collections.unmodifiableSet(new TreeSet<>(deniedCodes));
    }
  }

  public enum LegacyRule {
    ROLE_UNION,
    ACTIVE_DOMAIN_IDENTITY
  }

  public record Resource(
      String id,
      String type,
      String checkedCode,
      String accessMode,
      boolean enabled,
      String sourceFingerprint,
      List<String> ancestorIds,
      Set<String> managementCodes,
      LegacyRule rule) {
    public Resource {
      rule = rule == null ? LegacyRule.ROLE_UNION : rule;
      ancestorIds = List.copyOf(ancestorIds);
      managementCodes =
          managementCodes == null
              ? Set.of()
              : Collections.unmodifiableSet(new TreeSet<>(managementCodes));
    }

    public Resource(
        String id,
        String type,
        String checkedCode,
        String accessMode,
        boolean enabled,
        String sourceFingerprint,
        List<String> ancestorIds,
        Set<String> managementCodes) {
      this(
          id,
          type,
          checkedCode,
          accessMode,
          enabled,
          sourceFingerprint,
          ancestorIds,
          managementCodes,
          LegacyRule.ROLE_UNION);
    }

    public Resource(
        String id,
        String type,
        String checkedCode,
        String accessMode,
        boolean enabled,
        String sourceFingerprint,
        List<String> ancestorIds,
        String managementCode) {
      this(
          id,
          type,
          checkedCode,
          accessMode,
          enabled,
          sourceFingerprint,
          ancestorIds,
          managementCode == null ? Set.of() : Set.of(managementCode));
    }

    public Resource(
        String id,
        String type,
        String checkedCode,
        String accessMode,
        boolean enabled,
        String sourceFingerprint,
        List<String> ancestorIds) {
      this(id, type, checkedCode, accessMode, enabled, sourceFingerprint, ancestorIds, Set.of());
    }

    public Resource(
        String id,
        String type,
        String checkedCode,
        String accessMode,
        boolean enabled,
        String sourceFingerprint) {
      this(id, type, checkedCode, accessMode, enabled, sourceFingerprint, List.of(), Set.of());
    }
  }

  public record Bundle(
      String id, long effectiveVersion, Set<String> capabilityIds, String historyFingerprint) {
    public Bundle {
      capabilityIds = Collections.unmodifiableSet(new TreeSet<>(capabilityIds));
    }
  }
}
