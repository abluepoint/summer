package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.abluepoint.summer.sample.authorization.domain.migration.*;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorizationMigrationPlannerTest {
  @Test
  void blocksUnprovedPublicModesMissingBundleHistoryAndUnknownSourceRoles() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(),
            List.of(new Role("old", true, Set.of(), "missing-history")),
            List.of(new User("alice", true, Set.of("unknown-role"), Set.of(), "none")),
            List.of(
                new Resource("public", "INTERFACE", "read", "PUBLIC", true, "p"),
                new Resource("blank-menu", "MENU", null, "AUTHORIZED", true, "m"),
                new Resource("unproved-identity", "INTERFACE", null, "AUTHENTICATED", true, "i")),
            List.of(),
            List.of());
    var plan = new AuthorizationMigrationPlanner().plan(source);
    assertThat(plan.permissions()).isEmpty();
    assertThat(plan.blockers())
        .contains(
            "UNPROVEN_ACCESS_MODE:public",
            "SPECIAL_PUBLIC_RULE:blank-menu",
            "UNPROVEN_ACCESS_MODE:unproved-identity",
            "MISSING_BUNDLE_HISTORY:missing-history",
            "UNKNOWN_ROLE:alice:unknown-role");
  }

  @Test
  void preservesOnlyVerifiedActiveIdentityInterfaceRulesWithoutRoleGrants() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(),
            List.of(),
            List.of(new User("alice", true, Set.of(), Set.of(), "none")),
            List.of(
                new Resource(
                    "navigation",
                    "INTERFACE",
                    null,
                    "AUTHENTICATED",
                    true,
                    "exact-legacy-identity-rule",
                    List.of(),
                    Set.of(),
                    LegacyRule.ACTIVE_DOMAIN_IDENTITY)),
            List.of(),
            List.of());
    var plan = new AuthorizationMigrationPlanner().plan(source);
    assertThat(plan.blockers()).isEmpty();
    assertThat(plan.permissions())
        .extracting(MigrationPlan.Permission::accessMode)
        .containsExactly("AUTHENTICATED");
    assertThat(plan.roles().getFirst().permissionIds()).isEmpty();
    assertThat(plan.mappings()).anyMatch(m -> m.sourceKind().equals("LEGACY_RULE"));
  }

  @Test
  void narrowsEachUserAfterRoleUnionAndSplitsMenuFromInterface() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s1",
            "ENTERPRISE",
            "a",
            7,
            List.of(new Capability("c", "read", "alias", true)),
            List.of(new Role("old", true, Set.of("c"), null)),
            List.of(
                new User("alice", true, Set.of("old"), Set.of("read"), "no-data"),
                new User("bob", true, Set.of("old"), Set.of(), "no-data")),
            List.of(
                new Resource("i", "INTERFACE", "read", "AUTHORIZED", true, "exact-i"),
                new Resource("m", "MENU", "alias", "AUTHORIZED", true, "exact-m")),
            List.of(),
            List.of());
    var plan = new AuthorizationMigrationPlanner().plan(source);
    assertThat(plan.permissions())
        .extracting(MigrationPlan.Permission::type)
        .containsExactlyInAnyOrder("INTERFACE", "MENU");
    var alice =
        plan.assignments().stream()
            .filter(a -> a.subject().equals("alice"))
            .findFirst()
            .orElseThrow();
    var bob =
        plan.assignments().stream()
            .filter(a -> a.subject().equals("bob"))
            .findFirst()
            .orElseThrow();
    assertThat(alice.roleId()).isNotEqualTo(bob.roleId()).isNotEqualTo("old");
    var ar =
        plan.roles().stream().filter(r -> r.id().equals(alice.roleId())).findFirst().orElseThrow();
    assertThat(ar.permissionIds()).hasSize(1);
    assertThat(
            plan.permissions().stream().filter(p -> ar.permissionIds().contains(p.id())).toList())
        .extracting(MigrationPlan.Permission::resourceId)
        .containsExactly("m");
    assertThat(plan.blockers()).isEmpty();
  }

  @Test
  void freezesCallerCollectionsAndBlocksPlatformMembershipDependencies() {
    var caps = new ArrayList<Capability>();
    var snapshot =
        new LegacyAuthorizationSnapshot(
            "s",
            "PLATFORM",
            "platform",
            0,
            caps,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    caps.add(new Capability("late", "code", "alias", true));
    assertThat(snapshot.capabilities()).isEmpty();
    assertThat(new AuthorizationMigrationPlanner().plan(snapshot).blockers())
        .contains("PLATFORM_MEMBERSHIP_LIFECYCLE_REQUIRES_EQUIVALENCE");
  }

  @Test
  void retainsOldAncestorGateInsteadOfGrantingPreviouslyHiddenChildPage() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(new Capability("c", "child.read", "child", true)),
            List.of(new Role("old", true, Set.of("c"), null)),
            List.of(new User("alice", true, Set.of("old"), Set.of(), "none")),
            List.of(
                new Resource("parent", "MENU", "parent.read", "AUTHORIZED", true, "p"),
                new Resource(
                    "child", "MENU", "child.read", "AUTHORIZED", true, "c", List.of("parent"))),
            List.of(),
            List.of());
    assertThat(new AuthorizationMigrationPlanner().plan(source).roles().getFirst().permissionIds())
        .isEmpty();
  }

  @Test
  void expandsEachFrozenRoleFromItsMatchingHistoricalBundleVersion() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(
                new Capability("c", "read", "r", true), new Capability("d", "write", "w", true)),
            List.of(
                new Role("v1-role", true, Set.of("c"), "bundle"),
                new Role("v2-role", true, Set.of("c", "d"), "bundle")),
            List.of(
                new User("a", true, Set.of("v1-role"), Set.of(), "none"),
                new User("b", true, Set.of("v2-role"), Set.of(), "none")),
            List.of(
                new Resource("read", "INTERFACE", "read", "AUTHORIZED", true, "r"),
                new Resource("write", "INTERFACE", "write", "AUTHORIZED", true, "w")),
            List.of(
                new Bundle("bundle", 1, Set.of("c"), "trusted-full-history"),
                new Bundle("bundle", 2, Set.of("c", "d"), "trusted-full-history")),
            List.of());
    var plan = new AuthorizationMigrationPlanner().plan(source);
    assertThat(plan.blockers()).isEmpty();
    assertThat(plan.roles())
        .extracting(r -> r.permissionIds().size())
        .containsExactlyInAnyOrder(1, 2);
    assertThat(plan.mappings())
        .contains(
            new MigrationPlan.Mapping("BUNDLE_VERSION", "bundle:1", "SOURCE_ROLE", "v1-role"),
            new MigrationPlan.Mapping("BUNDLE_VERSION", "bundle:2", "SOURCE_ROLE", "v2-role"));
  }

  @Test
  void preservesExactPersistentManagementGuardInsteadOfAcceptingCapabilityAlias() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(new Capability("alias", "something.else", "enterprise.roles.read", true)),
            List.of(new Role("old", true, Set.of("alias"), null)),
            List.of(new User("alice", true, Set.of("old"), Set.of(), "none")),
            List.of(
                new Resource(
                    "permissions",
                    "INTERFACE",
                    "enterprise.roles.read",
                    "AUTHORIZED",
                    true,
                    "exact-management-handler",
                    List.of(),
                    "enterprise.roles.read")),
            List.of(),
            List.of());
    assertThat(new AuthorizationMigrationPlanner().plan(source).roles().getFirst().permissionIds())
        .isEmpty();
  }

  @Test
  void retainsEveryManagementGuardInAConjunction() {
    var source =
        new LegacyAuthorizationSnapshot(
            "s",
            "ENTERPRISE",
            "a",
            0,
            List.of(
                new Capability("read", "enterprise.roles.read", "r", true, Set.of("ENTERPRISE:a"))),
            List.of(new Role("old", true, Set.of("read"), null)),
            List.of(new User("alice", true, Set.of("old"), Set.of(), "none")),
            List.of(
                new Resource(
                    "management",
                    "INTERFACE",
                    "enterprise.roles.read",
                    "AUTHORIZED",
                    true,
                    "exact",
                    List.of(),
                    Set.of("enterprise.roles.read", "enterprise.roles.write"))),
            List.of(),
            List.of());
    assertThat(new AuthorizationMigrationPlanner().plan(source).roles().getFirst().permissionIds())
        .isEmpty();
  }
}
