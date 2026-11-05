package com.abluepoint.summer.sample.authorization.application.migration;

import static com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner.stableId;
import static com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.digest;

import com.abluepoint.summer.sample.authorization.LegacyDataScopeMigrationProof;
import com.abluepoint.summer.sample.authorization.LegacyDataScopeMigrationProof.Policy;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService.DataInput;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan.*;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan.Vector;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Plans only from a verified frozen input; no database reads or user-supplied mappings. */
public final class AuthorizationDataMigrationPlanner {
  public static final String VERSION = "summer-data-equivalence/2";
  private final ObjectMapper json;
  private final DataResourceRegistry registry;
  private final LegacyDataScopeMigrationProof proof = new LegacyDataScopeMigrationProof();

  public AuthorizationDataMigrationPlanner(ObjectMapper json, DataResourceRegistry registry) {
    this.json = json;
    this.registry = registry;
  }

  public String registryFingerprint() {
    return digest(json.writeValueAsString(registrations()));
  }

  private List<Map<String, Object>> registrations() {
    var result = new ArrayList<Map<String, Object>>();
    for (var resource : registry.list().stream().sorted(Comparator.comparing(DataResourceRegistry.Resource::key)).toList()) {
      Map<String, Object> row = json.readValue(json.writeValueAsString(resource), new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
      row.put("actions", new ArrayList<>(new TreeSet<>(resource.actions())));
      result.add(row);
    }
    return result;
  }

  public AuthorizationDataMigrationPlan plan(DataInput input) {
    var source = input.source();
    var evidence = json.readTree(input.evidenceJson());
    var blockers = new TreeSet<String>();
    var inventory = new ArrayList<Inventory>();
    var items = new ArrayList<Item>();
    var grants = new TreeMap<String, Grant>();
    var vectors = new ArrayList<Vector>();
    for (String blocker : input.plan().blockers())
      if (!blocker.startsWith("PENDING_DATA:")) blockers.add("IDENTITY:" + blocker);
    if (!input.domain().kind().equals("ENTERPRISE")) blockers.add("PLATFORM_DATA_LIFECYCLE_UNPROVED");
    if (evidence.path("legacyScopeCompatibility").asBoolean()) blockers.add("LEGACY_SCOPE_COMPATIBILITY_UNPROVED");
    if (!evidence.path("dataResourceRegistrations").equals(json.valueToTree(registrations())))
      blockers.add("FROZEN_DATA_REGISTRY_MISMATCH");
    if (!compilerFingerprint().equals(evidence.path("dataSemanticsClassHashes")
        .path("com.abluepoint.summer.sample.authorization.DataScopeExpression").asString()))
      blockers.add("FROZEN_LEGACY_COMPILER_MISMATCH");
    var policies = new TreeMap<String, JsonNode>();
    var bindings = new TreeMap<String, List<JsonNode>>();
    var registered = new TreeSet<String>();
    registry.list(input.domain()).forEach(r -> registered.add(r.key()));
    for (var row : evidence.path("policies")) {
      String resource = row.path("resource").asString();
      String path = "policies/" + row.path("policy_id").asString();
      inventory.add(new Inventory(path, resource, "UPPER", ""));
      validate(row, path, blockers);
      if (policies.put(resource, row) != null) blockers.add(path + ":DUPLICATE_RESOURCE_POLICY");
      if (!registered.contains(resource)) blockers.add(path + ":UNREGISTERED_RESOURCE:" + resource);
    }
    for (var row : evidence.path("dataBindings")) {
      String resource = row.path("resource").asString();
      String path = bindingPath(row);
      inventory.add(new Inventory(path, resource, row.path("subject_type").asString(), row.path("subject_id").asString()));
      validate(row, path, blockers);
      bindings.computeIfAbsent(resource, ignored -> new ArrayList<>()).add(row);
      if (!registered.contains(resource)) blockers.add(path + ":UNREGISTERED_RESOURCE:" + resource);
      if (!Set.of("ROLE", "USER").contains(row.path("subject_type").asString()))
        blockers.add(path + ":UNKNOWN_BINDING_SUBJECT_TYPE");
      if (!policies.containsKey(resource)) blockers.add(path + ":BINDING_WITHOUT_RESOURCE_POLICY");
    }
    var activeDepartments = new TreeSet<String>();
    for (var row : evidence.path("organizationUnits"))
      if (row.path("status").asString().equals("ACTIVE")) activeDepartments.add(row.path("unit_id").asString());
    var activeRoles = new TreeSet<String>();
    source.roles().stream().filter(r -> r.active()).forEach(r -> activeRoles.add(r.id()));
    // DEFAULT covers identities without persisted old links. A disabled subject/role
    // with retained links is a distinct frozen category: restoring it can revive DATA
    // without adding a link. Current denial is not a proof of permanent default denial.
    var boundDataRoles = new TreeSet<String>();
    bindings.forEach((resource, rows) -> {
      if (policies.containsKey(resource))
        rows.stream().filter(row -> row.path("subject_type").asString().equals("ROLE"))
            .forEach(row -> boundDataRoles.add(row.path("subject_id").asString()));
    });
    for (var user : source.users())
      for (String role : user.roleIds())
        if (boundDataRoles.contains(role)) {
          if (!user.active()) blockers.add("PERSISTED_ROLE_REACTIVATION_UNPROVED:" + user.subject() + ":" + role + ":subject-inactive");
          else if (!activeRoles.contains(role)) blockers.add("PERSISTED_ROLE_REACTIVATION_UNPROVED:" + user.subject() + ":" + role + ":role-inactive");
        }
    for (var resource : registry.list(input.domain()).stream().sorted(Comparator.comparing(DataResourceRegistry.Resource::key)).toList()) {
      var policy = policies.get(resource.key());
      var resourceBindings = bindings.getOrDefault(resource.key(), List.of());
      boolean hasRoles = resourceBindings.stream().anyMatch(b -> b.path("subject_type").asString().equals("ROLE"));
      for (String action : new TreeSet<>(resource.actions())) {
        if (resource.key().equals("applications") || !(Set.of("read", "create", "update").contains(action)
            || resource.key().equals("dictionaries") && action.equals("delete"))) {
          // applications has no legacy DATA consumer and DataReadService requires MANAGED. Configuration has no
          // legacy export consumer; dictionary draft deletion is a real legacy path.
          // Never manufacture actions from a policy's name.
          String reason = resource.key().equals("applications") ? "NEW_MODEL_ONLY_CONSUMER_REQUIRES_MANAGED_DOMAIN" : "NO_LEGACY_CONSUMER_FOR_ACTION";
          if (resource.key().equals("applications") && policy != null) blockers.add("policies/" + policy.path("policy_id").asString() + ":NO_LEGACY_APPLICATIONS_MAPPING");
          items.add(new Item("ALL", "", resource.key(), action, "NOT_APPLICABLE", List.of(), List.of(), reason, Map.of()));
          continue;
        }
        var subjects = new TreeMap<String, Set<String>>();
        for (var assignment : input.plan().assignments()) {
          var roles = new TreeSet<>(assignment.sourceRoleIds());
          roles.retainAll(activeRoles);
          subjects.put(assignment.subject(), roles);
        }
        // An unbound active member and every future/anonymous identity have no old role links.
        // Full resource ROLE existence is global, including inactive/unassigned bindings.
        addItem(input, "DEFAULT", "", resource.key(), action, policy, hasRoles,
            List.of(), List.of(), activeDepartments, items, grants, blockers);
        for (var subject : subjects.entrySet()) {
          var allows = new ArrayList<Policy>();
          var denies = new ArrayList<Policy>();
          for (var binding : resourceBindings) {
            String kind = binding.path("subject_type").asString();
            String id = binding.path("subject_id").asString();
            if (kind.equals("ROLE") && subject.getValue().contains(id)) allows.add(policy(binding, bindingPath(binding)));
            if (kind.equals("USER") && subject.getKey().equals(id)) denies.add(policy(binding, bindingPath(binding)));
          }
          addItem(input, "USER", subject.getKey(), resource.key(), action, policy, hasRoles,
              allows, denies, activeDepartments, items, grants, blockers);
        }
      }
    }
    for (var assignment : input.plan().assignments()) {
      var role = input.plan().roles().stream().filter(r -> r.id().equals(assignment.roleId())).findFirst().orElseThrow();
      var grantIds = items.stream().filter(i -> i.subjectKind().equals("USER") && i.subject().equals(assignment.subject()))
          .flatMap(i -> i.permissionIds().stream()).distinct().sorted().toList();
      var interfaceIds = role.permissionIds().stream().sorted().toList();
      String vectorHash = digest(json.writeValueAsString(List.of(interfaceIds, grantIds)));
      String target = stableId("data-role", input.domain().key(), vectorHash);
      vectors.add(new Vector(assignment.subject(), role.id(), target,
          assignment.sourceRoleIds().stream().sorted().toList(), interfaceIds, grantIds, vectorHash));
    }
    return new AuthorizationDataMigrationPlan(input.snapshotId(), input.domain().key(), input.sourceFingerprint(),
        VERSION, registryFingerprint(), List.copyOf(inventory), List.copyOf(items),
        List.copyOf(grants.values()), List.copyOf(vectors), List.copyOf(blockers));
  }

  private void addItem(DataInput input, String kind, String subject, String resource, String action,
      JsonNode upper, boolean hasRoles, List<Policy> allows, List<Policy> denies,
      Set<String> departments, List<Item> items, Map<String, Grant> grants, Set<String> blockers) {
    if (upper == null) {
      items.add(new Item(kind, subject, resource, action, "PROVED", List.of(), List.of(), "MISSING_OLD_POLICY_DENIES_SENSITIVE_RESOURCE", Map.of()));
      return;
    }
    var paths = new ArrayList<String>();
    paths.add("policies/" + upper.path("policy_id").asString());
    allows.forEach(p -> paths.add(p.sourcePath()));
    denies.forEach(p -> paths.add(p.sourcePath()));
    var result = kind.equals("DEFAULT")
        ? proof.proveDefaultAction(resource, action, policy(upper, paths.getFirst()), hasRoles, input.domain().id(), departments)
        : proof.proveAction(resource, action, policy(upper, paths.getFirst()), hasRoles, allows, denies, input.domain().id(), subject, departments);
    String reason = result.reason();
    boolean accepted = result.equivalent();
    var ids = new ArrayList<String>();
    if (accepted && !kind.equals("DEFAULT"))
      for (var scope : result.scopes()) {
        String id = stableId("data-permission", input.domain().key(), resource, action,
            scope.preset(), json.writeValueAsString(scope.departmentIds()));
        grants.putIfAbsent(id, new Grant(id, resource, action, scope.preset(), scope.departmentIds()));
        ids.add(id);
      }
    if (!accepted) blockers.add(kind + ":" + subject + ":" + resource + ":" + action + ":" + reason);
    items.add(new Item(kind, subject, resource, action, accepted ? "PROVED" : "BLOCKED",
        List.copyOf(paths), ids.stream().sorted().toList(), reason, result.witness()));
  }

  private Policy policy(JsonNode row, String path) {
    try {
      return new Policy(path, row.path("operator_name").asString(), decode(row.path("rules_json")), decode(row.path("conditions_json")));
    } catch (tools.jackson.core.JacksonException malformed) {
      // The report retains the source path, never raw policy JSON in an exception.
      return new Policy(path, "INVALID", null, null);
    }
  }
  private void validate(JsonNode row, String path, Set<String> blockers) {
    String error = proof.validationError(policy(row, path));
    if (!error.isEmpty()) blockers.add("INVALID_LEGACY_EXPRESSION:" + error);
  }
  private Object decode(JsonNode value) {
    return value.isMissingNode() || value.isNull() ? null : json.readValue(value.asString(), Object.class);
  }
  private static String bindingPath(JsonNode row) { return "dataBindings/" + row.path("binding_id").asString(); }

  private static String compilerFingerprint() {
    try (var bytes = AuthorizationDataMigrationPlanner.class.getClassLoader().getResourceAsStream(
        "com/abluepoint/summer/sample/authorization/DataScopeExpression.class")) {
      if (bytes == null) return "MISSING";
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Cannot identify frozen legacy compiler", failure);
    }
  }
}
