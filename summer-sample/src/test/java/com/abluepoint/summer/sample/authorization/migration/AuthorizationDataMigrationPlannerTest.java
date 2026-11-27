package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.abluepoint.summer.sample.authorization.LegacyDataScopeMigrationProof;
import com.abluepoint.summer.sample.authorization.LegacyDataScopeMigrationProof.Policy;
import com.abluepoint.summer.sample.authorization.LegacyDataScopeMigrationProof.Scope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationDataMigrationPlannerTest {
  private final LegacyDataScopeMigrationProof proof = new LegacyDataScopeMigrationProof();

  private Policy policy(String path, String field, Object value) {
    return new Policy(path, "AND", List.of(Map.of("field", field, "operator", "IN", "value", value)), null);
  }

  @Test
  void subtractsUserRowsFromCompleteRoleUnionWithoutCopyingSourceRoles() {
    var base = policy("policies/upper", "TENANT", List.of("tenant-a"));
    var first = policy("bindings/role-one", "DEPARTMENT", List.of("A", "B"));
    var second = policy("bindings/role-two", "DEPARTMENT", List.of("C"));
    var deny = policy("bindings/user", "DEPARTMENT", List.of("B"));
    var result = proof.prove(base, true, List.of(first, second), List.of(deny),
        "tenant-a", "alice", Set.of("A", "B", "C"));
    assertThat(result.equivalent()).isTrue();
    assertThat(result.scopes()).containsExactly(new Scope("SPECIFIED_DEPARTMENTS", List.of("A", "C")));
  }

  @Test
  void ownerScopeKeepsActualOwnerInsteadOfThePersonEnteringTheRecord() {
    var base = policy("upper", "TENANT", List.of("tenant-a"));
    var own = new Policy("role/owner", "AND", List.of(Map.of("field", "OWNER")), null);
    var result = proof.prove(base, true, List.of(own), List.of(), "tenant-a", "alice", Set.of());
    assertThat(result.scopes()).containsExactly(new Scope("SELF", List.of()));
  }

  @Test
  void creatorIsNotApproximatedAsOwnerAndReturnsAnIndependentCounterexample() {
    var base = policy("upper", "TENANT", List.of("tenant-a"));
    var creator = new Policy("role/creator", "AND", List.of(Map.of("field", "CREATOR")), null);
    var result = proof.prove(base, true, List.of(creator), List.of(), "tenant-a", "alice", Set.of());
    assertThat(result.equivalent()).isFalse();
    assertThat(result.witness()).containsEntry("creator", "alice").containsEntry("oldAllowed", "true");
    assertThat(result.witness().get("owner")).isNotEqualTo("alice");
  }

  @Test
  void subtractsTheWholeUserExpressionIncludingItsInternalDenial() {
    var base = policy("upper", "TENANT", List.of("tenant-a"));
    var roles = policy("role", "DEPARTMENT", List.of("A", "B"));
    var user = new Policy("user/deny", "OR", List.of(
        Map.of("field", "DEPARTMENT", "operator", "IN", "value", List.of("A", "B")),
        Map.of("field", "DEPARTMENT", "value", "B", "effect", "DENY")), null);
    var result = proof.prove(base, true, List.of(roles), List.of(user), "tenant-a", "alice", Set.of("A", "B"));
    assertThat(result.scopes()).containsExactly(new Scope("SPECIFIED_DEPARTMENTS", List.of("B")));
  }

  @Test
  void ownerAndDepartmentIntersectionCannotBecomeAPresetUnion() {
    var base = policy("upper", "DEPARTMENT", List.of("A"));
    var own = new Policy("role/owner", "AND", List.of(Map.of("field", "OWNER")), null);
    var result = proof.prove(base, true, List.of(own), List.of(), "tenant-a", "alice", Set.of("A"));
    assertThat(result.equivalent()).isFalse();
    assertThat(result.scopes()).isEmpty();
  }

  @Test
  void selectedContextAndDynamicTreeRequireTheirOwnDynamicEquivalence() {
    for (String field : List.of("ORGANIZATION", "ORGANIZATION_TREE")) {
      var base = new Policy("policies/dynamic", "AND", List.of(Map.of("field", field)), null);
      var result = proof.prove(base, false, List.of(), List.of(), "tenant-a", "alice", Set.of("A", "B"));
      assertThat(result.equivalent()).isFalse();
      assertThat(result.reason()).contains("policies/dynamic", "SINGLE_CONTEXT_OR_DYNAMIC_TREE");
    }
  }

  @Test
  void globalRoleBindingExistenceIsDifferentFromNoRoleBinding() {
    var base = policy("upper", "TENANT", List.of("tenant-a"));
    assertThat(proof.prove(base, true, List.of(), List.of(), "tenant-a", "future", Set.of()).scopes()).isEmpty();
    assertThat(proof.prove(base, false, List.of(), List.of(), "tenant-a", "future", Set.of()).scopes())
        .containsExactly(new Scope("TENANT_ALL", List.of()));
  }

  @Test
  void negativeOwnerAndNullSemanticsCannotBeSilentlyNarrowedToSelf() {
    var base = policy("upper", "TENANT", List.of("tenant-a"));
    var notOwner = new Policy("role/not-owner", "AND", List.of(Map.of("field", "OWNER", "operator", "NE")), null);
    assertThat(proof.prove(base, true, List.of(notOwner), List.of(), "tenant-a", "alice", Set.of()).equivalent()).isFalse();
  }

  @Test
  void inactiveExplicitDepartmentCannotLoseOldAllowedRowsUnderAnEmptyPreset() {
    var base = policy("upper", "DEPARTMENT", List.of("inactive"));
    var result = proof.prove(base, false, List.of(), List.of(), "tenant-a", "alice", Set.of());
    assertThat(result.equivalent()).isFalse();
    assertThat(result.witness()).containsEntry("organization", "inactive");
  }

  @Test
  void creationUsesTheTrustedConstructorOwnerRatherThanArbitraryExistingRowOwners() {
    var upper = new Policy("upper/not-owner", "AND", List.of(Map.of("field", "OWNER", "operator", "NE")), null);
    var result = proof.proveAction("dictionaries", "create", upper, false, List.of(), List.of(), "tenant-a", "alice", Set.of());
    assertThat(result.equivalent()).isTrue();
    assertThat(result.scopes()).isEmpty();
  }

  @Test
  void futureSubjectThatEqualsAPolicyLiteralCannotBeMissedByOneFreshRepresentative() {
    var upper = new Policy("upper/literal-identity", "AND", List.of(
        Map.of("field", "OWNER"), Map.of("field", "OWNER", "value", "future-literal")), null);
    var result = proof.proveDefault(upper, false, "tenant-a", Set.of());
    assertThat(result.equivalent()).isFalse();
    assertThat(result.reason()).contains("DEFAULT");
    assertThat(result.witness()).containsEntry("subject", "future-literal");
  }
  @Test
  void retainedRoleBindingOfAnInactiveSubjectCannotBeProvedByCurrentDenial() {
    assertThat(restorationPlan(false, true).blockers())
        .contains("PERSISTED_ROLE_REACTIVATION_UNPROVED:alice:old-role:subject-inactive");
  }

  @Test
  void retainedInactiveRoleCannotBeTreatedAsAnUnboundFutureSubject() {
    assertThat(restorationPlan(true, false).blockers())
        .contains("PERSISTED_ROLE_REACTIVATION_UNPROVED:alice:old-role:role-inactive");
  }

  @Test
  void dictionaryDraftDeletionIsARealLegacyActionAndCannotBeDroppedAsNotApplicable() {
    var plan = restorationPlan(true, true, com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform());
    assertThat(plan.blockers()).contains("PLATFORM_DATA_LIFECYCLE_UNPROVED");
    assertThat(plan.items()).anySatisfy(item -> {
      assertThat(item.subject()).isEqualTo("alice");
      assertThat(item.resource()).isEqualTo("dictionaries");
      assertThat(item.action()).isEqualTo("delete");
      assertThat(item.status()).isEqualTo("PROVED");
      assertThat(item.permissionIds()).isNotEmpty();
    });
    assertThat(plan.grants()).anySatisfy(grant -> {
      assertThat(grant.resource()).isEqualTo("dictionaries");
      assertThat(grant.action()).isEqualTo("delete");
      assertThat(grant.preset()).isEqualTo("TENANT_ALL");
    });
    assertThat(plan.grants()).noneMatch(grant -> grant.action().equals("export"));
  }

  private com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan
      restorationPlan(boolean subjectActive, boolean roleActive) {
    return restorationPlan(subjectActive, roleActive, com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("tenant-a"));
  }

  private com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan
      restorationPlan(boolean subjectActive, boolean roleActive, com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain) {
    var source = new com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot(
        "restoration", domain.kind(), domain.id(), 0, List.of(),
        List.of(new com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.Role(
            "old-role", roleActive, Set.of(), null)),
        List.of(new com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.User(
            "alice", subjectActive, Set.of("old-role"), Set.of(), "")), List.of(), List.of(), List.of());
    var original = new com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner().plan(source);
    var json = new tools.jackson.databind.ObjectMapper();
    String rules = json.writeValueAsString(List.of(Map.of("field", "TENANT")));
    String evidence = json.writeValueAsString(Map.of(
        "policies", List.of(Map.of("policy_id", "upper", "resource", "dictionaries", "operator_name", "AND", "rules_json", rules)),
        "dataBindings", List.of(Map.of("binding_id", "persisted", "resource", "dictionaries", "subject_type", "ROLE", "subject_id", "old-role", "operator_name", "AND", "rules_json", rules))));
    var input = new com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService.DataInput(
        source.snapshotId(), domain,
        0, 0, "f".repeat(64), source, original, evidence);
    return new com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationPlanner(
        json, new com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry()).plan(input);
  }

}
