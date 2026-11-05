package com.abluepoint.summer.sample.authorization.domain.migration;

import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.*;
import com.abluepoint.summer.sample.authorization.domain.migration.MigrationPlan.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Pure conversion of a trusted frozen snapshot; no authority is inferred from code prefixes. */
public final class AuthorizationMigrationPlanner {
  public MigrationPlan plan(LegacyAuthorizationSnapshot s) {
    if (!Set.of("ENTERPRISE", "PLATFORM").contains(s.domainKind())
        || s.domainId() == null
        || s.domainId().isBlank()
        || (s.domainKind().equals("PLATFORM") && !s.domainId().equals("platform")))
      throw new IllegalArgumentException("Explicit authorization domain required");
    String domain = s.domainKind() + ":" + s.domainId();
    List<String> blockers = new ArrayList<>(s.unresolved());
    if (s.domainKind().equals("PLATFORM"))
      blockers.add("PLATFORM_MEMBERSHIP_LIFECYCLE_REQUIRES_EQUIVALENCE");
    Map<String, Capability> caps = new TreeMap<>();
    Map<String, Role> oldRoles = new TreeMap<>();
    Map<String, List<Bundle>> bundles = new TreeMap<>();
    s.capabilities()
        .forEach(
            c -> {
              if (caps.put(c.id(), c) != null) blockers.add("DUPLICATE_CAPABILITY:" + c.id());
            });
    s.roles()
        .forEach(
            r -> {
              if (oldRoles.put(r.id(), r) != null) blockers.add("DUPLICATE_ROLE:" + r.id());
            });
    s.bundles()
        .forEach(
            b -> {
              var versions = bundles.computeIfAbsent(b.id(), ignored -> new ArrayList<>());
              if (versions.stream().anyMatch(v -> v.effectiveVersion() == b.effectiveVersion()))
                blockers.add("DUPLICATE_BUNDLE_VERSION:" + b.id() + ":" + b.effectiveVersion());
              versions.add(b);
            });
    List<Permission> permissions = new ArrayList<>();
    List<Mapping> mappings = new ArrayList<>();
    Map<String, Resource> consumers = new LinkedHashMap<>();
    Set<String> resourceKeys = new HashSet<>();
    for (Resource r : s.resources()) {
      if (!Set.of("INTERFACE", "MENU").contains(r.type())
          || r.id() == null
          || r.id().isBlank()
          || r.sourceFingerprint() == null
          || r.sourceFingerprint().isBlank()) {
        blockers.add("UNMAPPED_RESOURCE:" + r.id());
        continue;
      }
      if (!resourceKeys.add(r.type() + ":" + r.id())) {
        blockers.add("DUPLICATE_RESOURCE:" + r.id());
        continue;
      }
      boolean identityRule =
          r.rule() == LegacyRule.ACTIVE_DOMAIN_IDENTITY
              && r.type().equals("INTERFACE")
              && r.checkedCode() == null
              && r.managementCodes().isEmpty()
              && r.accessMode().equals("AUTHENTICATED");
      if (!"AUTHORIZED".equals(r.accessMode()) && !identityRule) {
        blockers.add("UNPROVEN_ACCESS_MODE:" + r.id());
        continue;
      }
      if (!identityRule && (r.checkedCode() == null || r.checkedCode().isBlank())) {
        blockers.add("SPECIAL_PUBLIC_RULE:" + r.id());
        continue;
      }
      String id = stableId("permission", domain, r.type(), r.id(), r.sourceFingerprint());
      permissions.add(new Permission(id, r.type(), r.id(), r.accessMode()));
      consumers.put(id, r);
      mappings.add(new Mapping("RESOURCE", r.type() + ":" + r.id(), "PERMISSION", id));
      if (identityRule)
        mappings.add(
            new Mapping("LEGACY_RULE", "ACTIVE_DOMAIN_IDENTITY:" + r.id(), "PERMISSION", id));
      else
        for (Capability c : caps.values())
          if (matches(c, r)) mappings.add(new Mapping("CAPABILITY", c.id(), "PERMISSION", id));
    }
    for (Role r : oldRoles.values()) {
      for (String id : r.capabilityIds())
        if (!caps.containsKey(id)) blockers.add("UNKNOWN_CAPABILITY:" + r.id() + ":" + id);
      if (r.bundleId() != null) {
        Bundle b =
            bundles.getOrDefault(r.bundleId(), List.of()).stream()
                .filter(v -> v.capabilityIds().equals(r.capabilityIds()))
                .max(Comparator.comparingLong(Bundle::effectiveVersion))
                .orElse(null);
        if (b == null
            || b.effectiveVersion() < 1
            || b.historyFingerprint() == null
            || b.historyFingerprint().isBlank())
          blockers.add("MISSING_BUNDLE_HISTORY:" + r.bundleId());
        else {
          if (!r.capabilityIds().equals(b.capabilityIds()))
            blockers.add("BUNDLE_EFFECTIVE_MISMATCH:" + r.id());
          mappings.add(
              new Mapping(
                  "BUNDLE_VERSION", b.id() + ":" + b.effectiveVersion(), "SOURCE_ROLE", r.id()));
        }
      }
    }
    Map<String, NarrowRole> narrowed = new TreeMap<>();
    List<Assignment> assignments = new ArrayList<>();
    Set<String> subjects = new HashSet<>();
    for (User u : s.users().stream().sorted(Comparator.comparing(User::subject)).toList()) {
      if (!subjects.add(u.subject())) {
        blockers.add("DUPLICATE_SUBJECT:" + u.subject());
        continue;
      }
      if (u.constraintFingerprint() == null || u.constraintFingerprint().isBlank())
        blockers.add("PENDING_DATA:" + u.subject());
      Set<String> granted = new TreeSet<>();
      for (String id : u.roleIds())
        if (!oldRoles.containsKey(id)) blockers.add("UNKNOWN_ROLE:" + u.subject() + ":" + id);
      if (u.active())
        for (var entry : consumers.entrySet()) {
          Resource resource = entry.getValue();
          if (!resource.enabled()) continue;
          boolean allow = granted(u, resource, caps, oldRoles, domain);
          if (allow
              && resource.ancestorIds().stream()
                  .allMatch(
                      ancestor ->
                          consumers.values().stream()
                              .filter(a -> a.type().equals("MENU") && a.id().equals(ancestor))
                              .findFirst()
                              .map(a -> granted(u, a, caps, oldRoles, domain))
                              .orElse(false))) granted.add(entry.getKey());
        }
      String constraints =
          u.constraintFingerprint() == null || u.constraintFingerprint().isBlank()
              ? "PENDING_DATA:" + u.subject()
              : u.constraintFingerprint();
      String rid = stableId("role", domain, canonical(granted), constraints);
      narrowed.putIfAbsent(rid, new NarrowRole(rid, Set.copyOf(granted), constraints));
      assignments.add(new Assignment(u.subject(), rid, Set.copyOf(u.roleIds())));
      mappings.add(new Mapping("USER", u.subject(), "ROLE", rid));
      for (String source : u.roleIds()) mappings.add(new Mapping("ROLE", source, "ROLE", rid));
    }
    for (Capability c : caps.values())
      if (mappings.stream()
          .noneMatch(m -> m.sourceKind().equals("CAPABILITY") && m.sourceId().equals(c.id())))
        mappings.add(new Mapping("CAPABILITY", c.id(), "INVENTORY", "NO_CURRENT_CONSUMER"));
    for (Role r : oldRoles.values())
      if (mappings.stream()
          .noneMatch(m -> m.sourceKind().equals("ROLE") && m.sourceId().equals(r.id())))
        mappings.add(new Mapping("ROLE", r.id(), "INVENTORY", "UNASSIGNED"));
    for (User u : s.users())
      for (String deny : u.deniedCodes())
        mappings.add(
            new Mapping(
                "USER_DENY", u.subject() + ":" + deny, "INVENTORY", "PRESERVED_BY_NARROWING"));
    for (Bundle b : s.bundles())
      if (mappings.stream()
          .noneMatch(
              m ->
                  m.sourceKind().equals("BUNDLE_VERSION")
                      && m.sourceId().equals(b.id() + ":" + b.effectiveVersion())))
        mappings.add(
            new Mapping(
                "BUNDLE_VERSION",
                b.id() + ":" + b.effectiveVersion(),
                "INVENTORY",
                "HISTORY_RETAINED"));
    return new MigrationPlan(
        s.snapshotId(),
        domain,
        s.revision(),
        List.copyOf(permissions),
        List.copyOf(narrowed.values()),
        List.copyOf(assignments),
        List.copyOf(mappings),
        blockers.stream().distinct().sorted().toList());
  }

  private static boolean granted(
      User user,
      Resource resource,
      Map<String, Capability> capabilities,
      Map<String, Role> roles,
      String domain) {
    if (!"AUTHORIZED".equals(resource.accessMode())
        || !resource.enabled()
        || user.deniedCodes().contains(resource.checkedCode())) return false;
    var effective =
        user.roleIds().stream()
            .map(roles::get)
            .filter(Objects::nonNull)
            .filter(Role::active)
            .flatMap(r -> r.capabilityIds().stream())
            .map(capabilities::get)
            .filter(Objects::nonNull)
            .filter(Capability::active)
            .toList();
    if (effective.stream().noneMatch(c -> matches(c, resource))) return false;
    return resource.managementCodes().stream()
        .allMatch(
            code ->
                !user.deniedCodes().contains(code)
                    && effective.stream()
                        .anyMatch(
                            c -> c.code().equals(code) && c.managementDomains().contains(domain)));
  }

  private static boolean matches(Capability c, Resource r) {
    return r.checkedCode().equals(c.code()) || r.checkedCode().equals(c.resource());
  }

  private static String canonical(Set<String> values) {
    return values.stream().sorted().map(v -> v.length() + ":" + v).reduce("", String::concat);
  }

  public static String stableId(String kind, String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts)
        digest.update((part.length() + ":" + part).getBytes(StandardCharsets.UTF_8));
      return "migration-" + kind + "-" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
