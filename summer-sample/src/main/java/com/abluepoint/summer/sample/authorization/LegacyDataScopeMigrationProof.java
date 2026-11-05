package com.abluepoint.summer.sample.authorization;

import java.util.*;

/** Frozen legacy compiler bridge; never consulted by managed request authorization. */
public final class LegacyDataScopeMigrationProof {
  public record Policy(String sourcePath, String operator, Object rules, Object conditions) {}
  public record Scope(String preset, List<String> departmentIds) {
    public Scope { departmentIds = List.copyOf(departmentIds); }
  }
  public record Proof(boolean equivalent, List<Scope> scopes, String reason, Map<String, String> witness) {
    public Proof {
      scopes = List.copyOf(scopes);
      witness = Collections.unmodifiableMap(new TreeMap<>(witness));
    }
  }

  public String validationError(Policy policy) {
    try {
      compile(policy, new DataScopeExpression.Context("__tenant__", "__subject__", "__context__", List::copyOf));
      return "";
    } catch (IllegalArgumentException invalid) { return policy.sourcePath() + ":" + invalid.getMessage(); }
  }

  public Proof proveAction(String resource, String action, Policy upper, boolean hasRoleBindings,
      List<Policy> roleAllows, List<Policy> userDenies, String tenant, String subject, Set<String> activeDepartments) {
    return evaluate(resource, action, upper, hasRoleBindings, roleAllows, userDenies, tenant, subject, activeDepartments);
  }

  public Proof proveDefault(Policy upper, boolean hasRoleBindings, String tenant, Set<String> activeDepartments) {
    return proveDefaultAction("", "read", upper, hasRoleBindings, tenant, activeDepartments);
  }

  public Proof proveDefaultAction(String resource, String action, Policy upper, boolean hasRoleBindings,
      String tenant, Set<String> activeDepartments) {
    if (hasRoleBindings) return new Proof(true, List.of(), "GLOBAL_ROLE_BINDING_DEFAULT_DENY", Map.of());
    var subjects = new TreeSet<String>();
    literalValues(upper.rules(), subjects);
    literalValues(upper.conditions(), subjects);
    String fresh = "__migration_future__";
    while (subjects.contains(fresh)) fresh += "_";
    subjects.add(fresh);
    var representatives = new ArrayList<String>(subjects);
    representatives.add(null);
    for (String subject : representatives) {
      var result = evaluate(resource, action, upper, false, List.of(), List.of(), tenant, subject, activeDepartments);
      if (!result.equivalent() || !result.scopes().isEmpty()) {
        var witness = new TreeMap<>(result.witness());
        witness.put("subject", printable(subject));
        return blocked("ANONYMOUS_UNBOUND_FUTURE_DEFAULT_UNPROVED: " + result.reason(), witness);
      }
    }
    return new Proof(true, List.of(), "DEFAULT_EMPTY_FOR_NULL_LITERAL_AND_FRESH_SUBJECT_CLASSES", Map.of());
  }

  public Proof prove(Policy upper, boolean hasRoleBindings, List<Policy> roleAllows,
      List<Policy> userDenies, String tenant, String subject, Set<String> activeDepartments) {
    return evaluate("", "read", upper, hasRoleBindings, roleAllows, userDenies, tenant, subject, activeDepartments);
  }

  private Proof evaluate(String resource, String action, Policy upper, boolean hasRoleBindings,
      List<Policy> roleAllows, List<Policy> userDenies, String tenant, String subject, Set<String> activeDepartments) {
    var policies = new ArrayList<Policy>();
    policies.add(upper);
    policies.addAll(roleAllows);
    policies.addAll(userDenies);
    for (var policy : policies)
      if (dynamicOrganization(policy.rules()) || dynamicOrganization(policy.conditions()))
        return blocked(policy.sourcePath() + ": SINGLE_CONTEXT_OR_DYNAMIC_TREE_NOT_ALL_APPOINTMENTS", Map.of());
    try {
      var context = new DataScopeExpression.Context(tenant, subject, null, List::copyOf);
      var base = compile(upper, context);
      var allows = roleAllows.stream().map(p -> compile(p, context)).toList();
      var denies = userDenies.stream().map(p -> compile(p, context)).toList();
      var expression = new DataScopeExpression.Junction(true, List.of(base,
          hasRoleBindings ? new DataScopeExpression.Junction(false, allows) : new DataScopeExpression.Constant(true),
          new DataScopeExpression.Not(new DataScopeExpression.Junction(false, denies))));
      return synthesize(expression, tenant, subject, activeDepartments, resource, action);
    } catch (IllegalArgumentException invalid) {
      return blocked("INVALID_LEGACY_EXPRESSION: " + invalid.getMessage(), Map.of());
    }
  }

  private DataScopeExpression.Node compile(Policy p, DataScopeExpression.Context context) {
    return DataScopeExpression.compile(p.operator(), p.rules(), p.conditions(), context).expression();
  }

  private Proof synthesize(DataScopeExpression.Node expression, String tenant, String subject,
      Set<String> activeDepartments, String resource, String action) {
    var values = new TreeSet<String>();
    collect(expression, values);
    values.add(tenant);
    if (subject != null) values.add(subject);
    String fresh = "__migration_other__";
    while (values.contains(fresh)) fresh += "_";
    var partition = new ArrayList<String>(values);
    partition.add(fresh);
    partition.add(null);
    if ((long) partition.size() * partition.size() * partition.size() > 100_000)
      return blocked("SYMBOLIC_PARTITION_LIMIT: no approximate conversion", Map.of());

    var candidates = new ArrayList<Scope>();
    candidates.add(new Scope("TENANT_ALL", List.of()));
    if (subject != null) candidates.add(new Scope("SELF", List.of()));
    for (String department : new TreeSet<>(activeDepartments))
      if (values.contains(department)) candidates.add(new Scope("SPECIFIED_DEPARTMENTS", List.of(department)));
    var rows = new ArrayList<DataScopeExpression.Row>();
    var expected = new BitSet();
    for (String organization : partition)
      for (String creator : partition)
        for (String owner : partition) {
          // Both real configuration constructors force creator/owner to the actor. Children
          // additionally retain the separately proved actual-parent UPDATE bound.
          if (action.equals("create") && (!Objects.equals(creator, subject) || !Objects.equals(owner, subject))) continue;
          // SELF in the old SQL/JPA compiler maps to created_by, not the actor/owner.
          var row = new DataScopeExpression.Row(tenant, organization, creator, owner, creator);
          if (resource.equals("dictionaries") && Set.of("update", "delete").contains(action) && expression.matches(row)
              && !expression.matches(new DataScopeExpression.Row(tenant, organization, subject, subject, subject)))
            return blocked("WRITE_ENTRYPOINT_GATE_NOT_REDUNDANT: publish/disable/rollback/draft also check actor-projected parent", Map.of(
                "organization", printable(organization), "creator", printable(creator), "owner", printable(owner), "projectedOwner", printable(subject)));
          if (expression.matches(row)) expected.set(rows.size());
          rows.add(row);
        }
    var actual = new BitSet();
    var safe = new ArrayList<Scope>();
    for (var candidate : candidates) {
      var covered = new BitSet();
      for (int i = 0; i < rows.size(); i++)
        if (matches(candidate, rows.get(i), subject)) covered.set(i);
      var extra = (BitSet) covered.clone();
      extra.andNot(expected);
      if (extra.isEmpty() && !covered.isEmpty()) {
        safe.add(candidate);
        actual.or(covered);
      }
    }
    if (!actual.equals(expected)) {
      var missing = (BitSet) expected.clone();
      missing.andNot(actual);
      int witness = missing.nextSetBit(0);
      // Prefer an allowed source row that demonstrates a delegated/NULL owner, when present.
      for (int i = witness; i >= 0; i = missing.nextSetBit(i + 1))
        if (!Objects.equals(subject, rows.get(i).owner())) { witness = i; break; }
      var row = rows.get(witness);
      return blocked("NOT_EXPRESSIBLE_AS_PRESET_UNION: old allowed row would be lost", Map.of(
          "tenant", tenant, "organization", printable(row.organization()),
          "creator", printable(row.creator()), "owner", printable(row.owner()),
          "oldAllowed", "true", "presetUnionAllowed", "false"));
    }
    if (safe.stream().anyMatch(s -> s.preset().equals("TENANT_ALL")))
      safe = new ArrayList<>(List.of(new Scope("TENANT_ALL", List.of())));
    else {
      var departments = new TreeSet<String>();
      for (var scope : safe) departments.addAll(scope.departmentIds());
      safe.removeIf(s -> s.preset().equals("SPECIFIED_DEPARTMENTS"));
      if (!departments.isEmpty()) safe.add(new Scope("SPECIFIED_DEPARTMENTS", List.copyOf(departments)));
    }
    return new Proof(true, List.copyOf(safe), expected.isEmpty() ? "PROVED_EMPTY" : "EXHAUSTIVE_EQUALITY_PARTITION", Map.of());
  }

  private static void literalValues(Object value, Set<String> values) {
    if (value instanceof Collection<?> collection) { collection.forEach(v -> literalValues(v, values)); return; }
    if (!(value instanceof Map<?, ?> node)) return;
    Object literal = node.get("value");
    if (literal instanceof String text) values.add(text);
    else if (literal instanceof Collection<?> collection)
      for (Object item : collection) if (item instanceof String text) values.add(text);
    literalValues(node.get("children"), values);
    literalValues(node.get("rules"), values);
  }

  private static boolean matches(Scope scope, DataScopeExpression.Row row, String subject) {
    return switch (scope.preset()) {
      case "TENANT_ALL" -> true;
      case "SELF" -> subject != null && subject.equals(row.owner());
      case "SPECIFIED_DEPARTMENTS" -> row.organization() != null && scope.departmentIds().contains(row.organization());
      default -> false;
    };
  }

  private static void collect(DataScopeExpression.Node node, Set<String> values) {
    if (node instanceof DataScopeExpression.Comparison comparison) values.addAll(comparison.values());
    else if (node instanceof DataScopeExpression.Junction junction)
      junction.children().forEach(child -> collect(child, values));
    else if (node instanceof DataScopeExpression.Not not) collect(not.child(), values);
  }

  private static boolean dynamicOrganization(Object value) {
    if (value instanceof Collection<?> values) return values.stream().anyMatch(LegacyDataScopeMigrationProof::dynamicOrganization);
    if (!(value instanceof Map<?, ?> node)) return false;
    Object field = node.get("field") == null ? node.get("type") : node.get("field");
    if (field instanceof String text) {
      String normalized = text.trim().toUpperCase(Locale.ROOT);
      if (normalized.equals("ORGANIZATION_TREE") || node.get("value") == null
          && Set.of("ORGANIZATION", "DEPARTMENT", "ORGANIZATION_ID", "ORG", "ORG_ID").contains(normalized)) return true;
    }
    return dynamicOrganization(node.get("children")) || dynamicOrganization(node.get("rules"));
  }

  private static String printable(String value) { return value == null ? "<NULL>" : value; }
  private static Proof blocked(String reason, Map<String, String> witness) {
    return new Proof(false, List.of(), reason, witness);
  }
}
