package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.*;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

/** Internal operator export, read from the live old tables and exact installed handlers. */
@Component
public class LegacyAuthorizationReader {
  public static final String EVIDENCE_FORMAT = "summer-legacy-source/1";
  public static final String MAPPING_FORMAT = "summer-authorization-mapping/1";

  public record Capture(
      LegacyAuthorizationSnapshot snapshot, String fingerprint, String evidenceJson) {}

  /** Trusted, frozen old HTTP export. Never accepted by a management HTTP endpoint. */
  public record RuntimeExport(
      List<Capability> capabilities, List<Bundle> bundles, String evidenceJson) {
    public RuntimeExport {
      capabilities = List.copyOf(capabilities);
      bundles = List.copyOf(bundles);
    }

    public static RuntimeExport empty() {
      return new RuntimeExport(List.of(), List.of(), "{}");
    }
  }

  @org.springframework.beans.factory.annotation.Value(
      "${summer.sample.authorization.legacy-scope-compatibility:false}")
  private boolean legacyScopeCompatibility;

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final List<
          com.abluepoint.summer.sample.authorization.infrastructure.permission
              .InterfaceRuntimeAdapter>
      runtimeAdapters;
  private final com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry
      dataResources;

  public LegacyAuthorizationReader(
      JdbcTemplate db,
      ObjectMapper json,
      ObjectProvider<RequestMappingHandlerMapping> mappings,
      List<
              com.abluepoint.summer.sample.authorization.infrastructure.permission
                  .InterfaceRuntimeAdapter>
          runtimeAdapters,
      com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry
          dataResources) {
    this.db = db;
    this.json = json;
    this.mappings = mappings;
    this.runtimeAdapters = List.copyOf(runtimeAdapters);
    this.dataResources = dataResources;
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public Capture read(Domain domain, long revision, RuntimeExport exported) {
    if (domain.kind().equals("PLATFORM")) return platformInventory(domain, revision, exported);
    var raw = legacyTables(domain);
    raw.put("legacyFactsFingerprint", factsFingerprint(raw));
    addDataSemantics(raw);
    raw.put(
        "sourceSchemaHistory",
        rows(
            "select version,description,script,checksum,success from flyway_schema_history where"
                + " success=true order by installed_rank"));
    raw.put("runtimeExport", exported);
    var blockers = new ArrayList<String>();
    boolean bridgeAnchor =
        mappings
            .orderedStream()
            .flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
            .anyMatch(
                entry ->
                    entry
                            .getValue()
                            .getBeanType()
                            .getName()
                            .equals(
                                "com.abluepoint.summer.sample.organization.OrganizationController")
                        && entry.getValue().getMethod().getName().equals("tree")
                        && entry
                            .getKey()
                            .getPatternValues()
                            .contains("/api/v1/enterprises/{enterpriseId}/org-tree")
                        && "enterprise.organization.read".equals(annotationCode(entry.getValue())));
    raw.put(
        "captureCompatibility",
        Map.of(
            "sourceRecipe",
            "summer-sample-legacy-bridge/1",
            "legacyAnnotationAnchor",
            bridgeAnchor));
    if (!bridgeAnchor) blockers.add("UNSUPPORTED_LEGACY_CAPTURE_SOURCE_VERSION");
    if (legacyScopeCompatibility) blockers.add("LEGACY_SCOPE_COMPATIBILITY_REQUIRES_EQUIVALENCE");
    validateExport(domain, exported, blockers);
    var capabilities = new TreeMap<String, Capability>();
    exported
        .capabilities()
        .forEach(
            c ->
                capabilities.put(
                    c.id(), new Capability(c.id(), c.code(), c.resource(), c.active())));
    for (var row : table(raw, "capabilities")) {
      var c =
          new Capability(
              text(row, "capability_id"),
              text(row, "code"),
              text(row, "resource"),
              "ACTIVE".equals(row.get("status")),
              managementDomains(row));
      var old = capabilities.put(c.id(), c);
      if (old != null
          && (!old.code().equals(c.code())
              || !old.resource().equals(c.resource())
              || old.active() != c.active())) blockers.add("RUNTIME_CAPABILITY_CHANGED:" + c.id());
    }
    var roles =
        table(raw, "roles").stream()
            .map(
                r ->
                    new Role(
                        text(r, "role_id"),
                        "ACTIVE".equals(r.get("status")),
                        csv(text(r, "capability_ids")),
                        nullable(r, "bundle_id")))
            .toList();
    var users = new ArrayList<User>();
    for (var binding : table(raw, "bindings")) {
      String subject = text(binding, "username");
      boolean active =
          table(raw, "enterprise").stream().anyMatch(e -> "ACTIVE".equals(e.get("status")))
              && table(raw, "members").stream()
                  .anyMatch(
                      m -> subject.equals(m.get("subject")) && "ACTIVE".equals(m.get("status")))
              && table(raw, "identities").stream()
                  .anyMatch(
                      u ->
                          subject.equals(u.get("username"))
                              && Boolean.TRUE.equals(u.get("active")));
      var ids = stringSet(text(binding, "role_ids"));
      var restrictions =
          table(raw, "dataBindings").stream()
              .filter(
                  r ->
                      "USER".equals(r.get("subject_type")) && subject.equals(r.get("subject_id"))
                          || "ROLE".equals(r.get("subject_type"))
                              && ids.contains(r.get("subject_id")))
              .toList();
      var denied = stringSet(text(binding, "denied_capability_codes"));
      // Include every old role and deny until13 proves which DATA meanings they carry.
      String constraint =
          digest(
              json.writeValueAsString(
                  List.of(
                      "UNCONVERTED_LEGACY_DEFAULTS",
                      ids,
                      denied,
                      table(raw, "policies"),
                      restrictions)));
      blockers.add("PENDING_DATA:" + subject);
      users.add(new User(subject, active, ids, denied, constraint));
    }
    var runtimeInventory = runtimeInventory(domain, table(raw, "interfaces"), blockers);
    raw.put("runtimeResourceMappings", runtimeInventory);
    var blockedRuntimeIds =
        runtimeInventory.stream()
            .map(r -> r.get("resourceId").toString())
            .collect(java.util.stream.Collectors.toSet());
    var exactSources = new ArrayList<Map<String, String>>();
    var resources =
        interfaces(
            domain,
            table(raw, "interfaces"),
            table(raw, "interfaceHistory"),
            blockers,
            exactSources,
            blockedRuntimeIds);
    // Arbitrary old public rules are not representable by an inferred PUBLIC permission.
    for (var r : table(raw, "resourcePolicies"))
      if (Boolean.TRUE.equals(r.get("public_access")))
        blockers.add("SPECIAL_PUBLIC_RULE:" + r.get("resource_id"));
    menus(raw, resources, blockers);
    if (domain.id().equals("ent-acme"))
      blockers.add("BOOTSTRAP_AND_SCOPE_RULES_REQUIRE_EQUIVALENCE");
    raw.put("effectiveResources", resources);
    raw.put("exactResourceMappings", exactSources);
    raw.put(
        "sourceProjectionHash",
        projectionFingerprint(
            new LegacyAuthorizationSnapshot(
                "",
                domain.kind(),
                domain.id(),
                0,
                List.copyOf(capabilities.values()),
                roles,
                users,
                resources,
                exported.bundles(),
                blockers)));
    String evidence = json.writeValueAsString(ordered(raw));
    String fingerprint = digest(evidence);
    String id = AuthorizationMigrationPlanner.stableId("snapshot", domain.key(), fingerprint);
    return new Capture(
        new LegacyAuthorizationSnapshot(
            id,
            domain.kind(),
            domain.id(),
            revision,
            List.copyOf(capabilities.values()),
            roles,
            users,
            resources,
            exported.bundles(),
            blockers),
        fingerprint,
        evidence);
  }

  /** Hashes only the frozen typed projection; no database or installed-code access. */
  public String projectionFingerprint(LegacyAuthorizationSnapshot snapshot) {
    return digest(
        json.writeValueAsString(
            List.of(
                snapshot.domainKind(),
                snapshot.domainId(),
                snapshot.capabilities(),
                snapshot.roles(),
                snapshot.users(),
                snapshot.resources(),
                snapshot.bundles(),
                snapshot.unresolved())));
  }

  private Map<String, Object> legacyTables(Domain domain) {
    if (!domain.kind().equals("ENTERPRISE"))
      throw new IllegalArgumentException("Enterprise bridge facts required");
    var raw = new TreeMap<String, Object>();
    raw.put(
        "capabilities",
        rows(
            "select * from summer_authorization_capability where enterprise_id=? or enterprise_id"
                + " is null order by capability_id",
            domain.id()));
    raw.put(
        "roles",
        rows(
            "select r.* from summer_authorization_role r where r.enterprise_id=? and not"
                + " exists(select 1 from summer_permission_role p where p.role_id=r.role_id) order"
                + " by r.role_id",
            domain.id()));
    raw.put(
        "roleCapabilityLinks",
        rows(
            "select c.* from summer_authorization_role_capability c join summer_authorization_role"
                + " r on r.role_id=c.role_id where r.enterprise_id=? order by"
                + " c.role_id,c.capability_id",
            domain.id()));
    raw.put(
        "dataPolicyHistory",
        rows(
            "select * from summer_data_scope_policy_audit where enterprise_id=? order by"
                + " occurred_at,audit_id",
            domain.id()));
    raw.put(
        "bindings",
        rows(
            "select * from summer_authorization_user_binding where enterprise_id=? order by"
                + " username",
            domain.id()));
    raw.put(
        "enterprise",
        rows(
            "select enterprise_id,status,version from summer_enterprise where enterprise_id=?",
            domain.id()));
    raw.put(
        "members",
        rows(
            "select member_id,subject,status,version from summer_enterprise_member where"
                + " enterprise_id=? order by subject",
            domain.id()));
    raw.put(
        "organizationUnits",
        rows(
            "select * from summer_organization_unit where enterprise_id=? order by unit_id",
            domain.id()));
    raw.put(
        "organizationMemberships",
        rows(
            "select * from summer_organization_membership where enterprise_id=? order by"
                + " member_id,organization_id",
            domain.id()));
    // Include selected contexts for current members even if the selected enterprise differs.
    raw.put(
        "organizationContexts",
        rows(
            "select c.* from summer_organization_context c where c.enterprise_id=? or exists(select"
                + " 1 from summer_enterprise_member m where m.enterprise_id=? and"
                + " m.subject=c.subject) order by c.subject",
            domain.id(),
            domain.id()));
    raw.put(
        "identities",
        rows(
            "select u.username,u.active from summer_auth_user u where exists(select 1 from"
                + " summer_enterprise_member m where m.enterprise_id=? and m.subject=u.username) or"
                + " exists(select 1 from summer_authorization_user_binding b where"
                + " b.enterprise_id=? and b.username=u.username) order by u.username",
            domain.id(),
            domain.id()));
    raw.put(
        "policies",
        rows(
            "select * from summer_data_scope_policy where enterprise_id=? order by policy_id",
            domain.id()));
    raw.put(
        "dataBindings",
        rows(
            "select * from summer_data_scope_binding where enterprise_id=? order by binding_id",
            domain.id()));
    raw.put(
        "interfaceHistory",
        rows("select * from summer_interface_resource_revision order by revision"));
    raw.put(
        "interfaces",
        rows(
            "select * from summer_interface_resource where domain_kind=? order by resource_id",
            domain.kind()));
    raw.put(
        "resourcePolicies",
        rows("select * from summer_authorization_resource_policy order by resource_id"));
    raw.put(
        "menuPublication", rows("select * from summer_menu_publication order by publication_id"));
    raw.put("menus", rows("select * from summer_menu_manifest_item order by menu_id"));
    raw.put("menuOverrides", List.of());
    return raw;
  }

  private String factsFingerprint(Map<String, Object> raw) {
    var facts = new TreeMap<String, Object>(raw);
    // Discovery and installed-code metadata may change after the approved bridge.
    // Their original exact provenance remains hash-bound in immutable evidence.
    facts.remove("interfaces");
    facts.remove("interfaceHistory");
    return digest(json.writeValueAsString(ordered(facts)));
  }

  /** Current persistent legacy facts only; no annotation/code lookup or recapture. */
  public String currentLegacyFactsFingerprint(Domain domain) {
    return factsFingerprint(legacyTables(domain));
  }

  private List<Map<String, Object>> runtimeInventory(
      Domain domain, List<Map<String, Object>> catalog, List<String> blockers) {
    var result = new ArrayList<Map<String, Object>>();
    // Exact installed registration agreed with the runtime owner. It is inventory only:
    // removing the old signed capability claim has not been proved non-expanding.
    for (var adapter : runtimeAdapters) {
      if (!(adapter
          instanceof
          com.abluepoint.summer.sample.authorization.infrastructure.permission
              .BusinessRuntimeResources)) continue;
      for (var mapping : adapter.mappings()) {
        if (!mapping.application().equals("application")
            || !mapping.source().equals("RUNTIME")
            || !mapping.method().equals("WS")
            || !mapping.path().equals("/ws/v1/messages")
            || !mapping.domainKind().equals(domain.kind())
            || !domain.kind().equals("ENTERPRISE")
            || !mapping.configurable()) continue;
        String prefix = "com.abluepoint.summer.sample.realtime.RealtimeAccess@realtimeAccess#";
        String code;
        if (mapping.handler().equals(prefix + "read")
            && mapping.conditions().equals(Map.of("frame", List.of("ping", "read"))))
          code = "enterprise.messages.read";
        else if (mapping.handler().equals(prefix + "send")
            && mapping.conditions().equals(Map.of("frame", List.of("ack", "send"))))
          code = "enterprise.messages.write";
        else continue;
        String locator =
            digest(
                json.writeValueAsString(
                    List.of(
                        mapping.application(),
                        mapping.source(),
                        mapping.method(),
                        mapping.path(),
                        mapping.conditions())));
        String handler =
            digest(
                json.writeValueAsString(
                    List.of(mapping.handler(), mapping.domainKind(), mapping.configurable())));
        for (var row : catalog) {
          if (!Boolean.TRUE.equals(row.get("active"))
              || !Boolean.TRUE.equals(row.get("configurable"))
              || !locator.equals(row.get("locator_hash"))
              || !handler.equals(row.get("handler_hash"))) continue;
          String id = text(row, "resource_id");
          blockers.add("RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED:" + id);
          var item = new TreeMap<String, Object>();
          item.put("resourceId", id);
          item.put("installedHandler", mapping.handler());
          item.put("source", "RUNTIME");
          item.put("method", mapping.method());
          item.put("path", mapping.path());
          item.put("conditions", mapping.conditions());
          item.put("oldCheckedCode", code);
          item.put(
              "oldRule",
              "nonexpired signed JWT + signed active tenant membership + exact signed legacy"
                  + " capability claim + exact scope + current membership + persisted legacy"
                  + " decision");
          item.put(
              "additionalUpperBound",
              "send/ack also require current read; tenant row, origin, cursor, TTL, session and"
                  + " frame bounds retained by runtime owner");
          item.put("status", "BLOCKED");
          item.put(
              "reason",
              "MANAGED removes signed legacy capability claim; old credential non-expansion"
                  + " unproved");
          item.put("policyVersion", "legacy-realtime-exact-code-and-signed-claim-v1");
          result.add(item);
        }
      }
    }
    return result.stream()
        .sorted(Comparator.comparing(r -> r.get("resourceId").toString()))
        .toList();
  }

  private void addDataSemantics(Map<String, Object> raw) {
    raw.put("evidenceFormat", EVIDENCE_FORMAT);
    raw.put("mappingFormat", MAPPING_FORMAT);
    raw.put("legacyScopeCompatibility", legacyScopeCompatibility);

    var registrations = new ArrayList<Map<String, Object>>();
    for (var resource :
        dataResources.list().stream()
            .sorted(
                Comparator.comparing(
                    com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry
                            .Resource
                        ::key))
            .toList()) {
      Map<String, Object> entry = json.readValue(json.writeValueAsString(resource), Map.class);
      entry.put("actions", new ArrayList<>(new TreeSet<>(resource.actions())));
      registrations.add(entry);
    }
    raw.put("dataResourceRegistrations", registrations);
    raw.put(
        "legacyOrganizationSemantics",
        Map.of(
            "jwtProjection",
                "LocalConsoleIdentityClaims emits selected persisted context only after active"
                    + " member, unit, appointment and token membership checks",
            "readContext",
                "DataScopeService prefers signed organization claim, verifies enterprise and active"
                    + " unit, then falls back to persisted selected context; not all appointments",
            "coverage",
                "Signed token values, anonymous/default/unbound/future subjects require separate"
                    + " equivalence proof; never inferred from current rows"));
    var hashes = new TreeMap<String, String>();
    for (String name :
        List.of(
            "com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader",
            "com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner",
            "com.abluepoint.summer.sample.authorization.AuthorizationService",
            "com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources",
            "com.abluepoint.summer.sample.identity.LocalConsoleIdentityClaims",
            "com.abluepoint.summer.sample.authorization.DataScopeService",
            "com.abluepoint.summer.sample.authorization.DataScopeExpression",
            "com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry")) {
      try (var stream =
          getClass().getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
        if (stream == null)
          throw new IllegalStateException("Missing installed DATA semantics " + name);
        hashes.put(
            name,
            HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
      } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
        throw new IllegalStateException("Cannot fingerprint installed DATA semantics", failure);
      }
    }
    raw.put("dataSemanticsClassHashes", hashes);
  }

  private Capture platformInventory(Domain domain, long revision, RuntimeExport exported) {
    // Old platform grants came from roles across active enterprise memberships, never
    // from a tenant selected by the platform sentinel string.
    var raw = new TreeMap<String, Object>();
    addDataSemantics(raw);
    raw.put("runtimeExport", exported);
    raw.put(
        "roleCapabilityLinks",
        rows(
            "select c.* from summer_authorization_role_capability c join summer_authorization_role"
                + " r on r.role_id=c.role_id where not exists(select 1 from summer_permission_role"
                + " p where p.role_id=r.role_id) order by"
                + " r.enterprise_id,c.role_id,c.capability_id"));
    raw.put(
        "policies",
        rows("select * from summer_data_scope_policy order by enterprise_id,policy_id"));
    raw.put(
        "dataBindings",
        rows("select * from summer_data_scope_binding order by enterprise_id,binding_id"));
    raw.put(
        "dataPolicyHistory",
        rows(
            "select * from summer_data_scope_policy_audit order by"
                + " enterprise_id,occurred_at,audit_id"));

    raw.put(
        "globalPlatformCapabilities",
        rows(
            "select * from summer_authorization_capability where scope='PLATFORM' and enterprise_id"
                + " is null order by capability_id"));
    raw.put(
        "membershipScopedRoles",
        rows(
            "select r.* from summer_authorization_role r where not exists(select 1 from"
                + " summer_permission_role p where p.role_id=r.role_id) order by"
                + " r.enterprise_id,r.role_id"));
    raw.put(
        "membershipScopedBindings",
        rows("select * from summer_authorization_user_binding order by enterprise_id,username"));
    raw.put(
        "enterprises",
        rows("select enterprise_id,status,version from summer_enterprise order by enterprise_id"));
    raw.put(
        "members",
        rows(
            "select enterprise_id,member_id,subject,status,version from summer_enterprise_member"
                + " order by enterprise_id,subject"));
    raw.put("identities", rows("select username,active from summer_auth_user order by username"));
    raw.put(
        "organizationUnits",
        rows("select * from summer_organization_unit order by enterprise_id,unit_id"));
    raw.put(
        "organizationMemberships",
        rows(
            "select * from summer_organization_membership order by"
                + " enterprise_id,member_id,organization_id"));
    raw.put(
        "organizationContexts", rows("select * from summer_organization_context order by subject"));
    raw.put(
        "interfaceHistory",
        rows("select * from summer_interface_resource_revision order by revision"));
    raw.put(
        "interfaces",
        rows(
            "select * from summer_interface_resource where domain_kind='PLATFORM' order by"
                + " resource_id"));
    raw.put("menus", rows("select * from summer_menu_manifest_item order by menu_id"));
    String evidence = json.writeValueAsString(ordered(raw));
    String fingerprint = digest(evidence);
    return new Capture(
        new LegacyAuthorizationSnapshot(
            AuthorizationMigrationPlanner.stableId("snapshot", domain.key(), fingerprint),
            domain.kind(),
            domain.id(),
            revision,
            table(raw, "globalPlatformCapabilities").stream()
                .map(
                    c ->
                        new Capability(
                            text(c, "capability_id"),
                            text(c, "code"),
                            text(c, "resource"),
                            "ACTIVE".equals(c.get("status")),
                            managementDomains(c)))
                .toList(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("PLATFORM_MEMBERSHIP_LIFECYCLE_REQUIRES_EQUIVALENCE")),
        fingerprint,
        evidence);
  }

  private List<Resource> interfaces(
      Domain d,
      List<Map<String, Object>> catalog,
      List<Map<String, Object>> history,
      List<String> blockers,
      List<Map<String, String>> exactSources,
      Set<String> blockedRuntimeIds) {
    var exact = new HashMap<String, String>();
    var legacyInstalled = new HashMap<String, String>();
    var identityRules = new HashSet<String>();
    var components = new HashSet<String>();
    var guards = new HashMap<String, Set<String>>();
    mappings
        .orderedStream()
        .forEach(
            mapping ->
                mapping
                    .getHandlerMethods()
                    .forEach(
                        (info, handler) -> {
                          var methods = new TreeSet<String>();
                          info.getMethodsCondition()
                              .getMethods()
                              .forEach(m -> methods.add(m.name()));
                          if (methods.isEmpty())
                            methods.addAll(
                                List.of(
                                    "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS",
                                    "TRACE"));
                          if (methods.contains("GET")) methods.add("HEAD");
                          for (String path : info.getPatternValues())
                            for (String method : methods) {
                              var installed =
                                  ManagedInterfaceResources.mvc(method, path, handler, info);
                              var legacyConditions =
                                  new TreeMap<String, List<String>>(installed.conditions());
                              if (info.getVersionCondition().isEmpty())
                                legacyConditions.remove("version");
                              String legacy =
                                  legacyIdentity(
                                      "application",
                                      "MVC",
                                      method,
                                      path,
                                      handler.getBeanType().getName()
                                          + "#"
                                          + handler.getMethod().getName()
                                          + Arrays.toString(
                                              handler.getMethod().getParameterTypes()),
                                      legacyConditions,
                                      checkedCode(handler, method, path));
                              if (!installed.configurable()) {
                                components.add(legacy);
                                continue;
                              }
                              if (!installed.domainKind().equals(d.kind())) continue;
                              String locator =
                                  digest(
                                      json.writeValueAsString(
                                          List.of(
                                              installed.application(),
                                              installed.source(),
                                              installed.method(),
                                              installed.path(),
                                              installed.conditions())));
                              String key =
                                  locator
                                      + ":"
                                      + digest(
                                          json.writeValueAsString(
                                              List.of(
                                                  installed.handler(),
                                                  installed.domainKind(),
                                                  installed.configurable())));
                              legacyInstalled.put(legacy, key);
                              String code =
                                  handler
                                          .getBeanType()
                                          .getName()
                                          .equals(
                                              "com.abluepoint.summer.sample.authorization.api.permission.PermissionController")
                                      ? managementCode(d, method)
                                      : checkedCode(handler, method, path);
                              String owner = handler.getBeanType().getName();
                              String handlerVerb =
                                  info.getMethodsCondition().getMethods().stream()
                                          .anyMatch(m -> m.name().equals("GET"))
                                      ? "GET"
                                      : method;
                              if (owner.equals(
                                  "com.abluepoint.summer.sample.authorization.api.permission.PermissionController")) {
                                var required = new TreeSet<String>();
                                required.add(managementCode(d, method));
                                required.add(managementCode(d, handlerVerb));
                                guards.put(key, Set.copyOf(required));
                              } else if (owner.equals(
                                  "com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController"))
                                guards.put(key, Set.of(managementCode(d, method)));
                              else if (Set.of(
                                          "InterfacePermissionController",
                                          "MenuDirectoryController",
                                          "MenuPermissionController",
                                          "PermissionDataScopeController")
                                      .contains(handler.getBeanType().getSimpleName())
                                  && handler
                                      .getBeanType()
                                      .getPackageName()
                                      .equals(
                                          "com.abluepoint.summer.sample.authorization.api.permission"))
                                guards.put(key, Set.of(managementCode(d, handlerVerb)));
                              else if (owner.equals(
                                  "com.abluepoint.summer.sample.authorization.api.permission.PermissionDataController"))
                                guards.put(key, Set.of(managementCode(d, "GET")));
                              // These two exact GET handlers bypassed capability evaluation in
                              // the old interceptor, after current active domain identity checks.
                              if (method.equals("GET")
                                  && ((owner.equals(
                                              "com.abluepoint.summer.sample.menu.MenuManifestController")
                                          && handler.getMethod().getName().equals("visible")
                                          && path.equals(
                                              "/api/v1/enterprises/{enterpriseId}/menu-manifest"))
                                      || (owner.equals(
                                              "com.abluepoint.summer.sample.authorization.api.permission.PermissionAccessController")
                                          && handler.getMethod().getName().equals("navigation")
                                          && path.equals(
                                              "/api/v1/enterprises/{enterpriseId}/authorization/navigation"))))
                                identityRules.add(key);
                              if (exact.putIfAbsent(key, code) != null)
                                blockers.add("AMBIGUOUS_INSTALLED_HANDLER:" + locator);
                            }
                        }));
    var resources = new ArrayList<Resource>();
    var currentIds = new HashMap<String, String>();
    for (var row : catalog) {
      if (!Boolean.TRUE.equals(row.get("active")) || !Boolean.TRUE.equals(row.get("configurable")))
        continue;
      String id = text(row, "resource_id");
      if (blockedRuntimeIds.contains(id)) continue;
      String code = exact.get(text(row, "locator_hash") + ":" + text(row, "handler_hash"));
      if (code == null) {
        blockers.add("UNMAPPED_INTERFACE:" + id);
        continue;
      }
      currentIds.put(text(row, "locator_hash") + ":" + text(row, "handler_hash"), id);
      String key = text(row, "locator_hash") + ":" + text(row, "handler_hash");
      boolean identity = identityRules.contains(key);
      var requiredGuards = guards.getOrDefault(key, Set.of());
      var proof = new TreeMap<String, Object>(row);
      proof.put("legacyRule", identity ? "ACTIVE_DOMAIN_IDENTITY" : "ROLE_UNION");
      proof.put("checkedCode", identity ? "" : code);
      proof.put("managementCodes", new TreeSet<>(requiredGuards));
      resources.add(
          new Resource(
              id,
              "INTERFACE",
              identity ? null : code,
              identity ? "AUTHENTICATED" : "AUTHORIZED",
              true,
              digest(json.writeValueAsString(proof)),
              List.of(),
              requiredGuards,
              identity
                  ? LegacyAuthorizationSnapshot.LegacyRule.ACTIVE_DOMAIN_IDENTITY
                  : LegacyAuthorizationSnapshot.LegacyRule.ROLE_UNION));
    }
    if (history.isEmpty()) blockers.add("MISSING_INTERFACE_DISCOVERY_HISTORY");
    else {
      var stored = json.readTree(history.getLast().get("snapshot_json").toString());
      var items = stored.isArray() ? stored : stored.path("items");
      if (!stored.isArray() && !stored.path("issues").isEmpty())
        blockers.add("INCOMPLETE_LEGACY_DISCOVERY");
      for (var item : items) {
        String path = item.path("normalizedPath").asString();
        boolean scoped =
            d.kind().equals("PLATFORM")
                ? path.startsWith("/api/v1/platform/")
                : path.startsWith("/api/v1/enterprises/{enterpriseId}/");
        if (!scoped || !"BUSINESS".equals(item.path("resourceType").asString())) continue;
        Map<String, List<String>> conditions = new TreeMap<>();
        item.path("conditions")
            .properties()
            .forEach(
                entry -> {
                  var values = new ArrayList<String>();
                  entry.getValue().forEach(v -> values.add(v.asString()));
                  conditions.put(entry.getKey(), values);
                });
        String legacy =
            legacyIdentity(
                item.path("contextId").asString(),
                item.path("source").asString(),
                item.path("httpMethod").asString(),
                path,
                item.path("handler").asString(),
                conditions,
                item.path("permissionCode").asString());
        if (components.contains(legacy)) {
          exactSources.add(
              Map.of(
                  "legacyIdentity",
                  legacy,
                  "resourceId",
                  "COMPONENT_RETAINED",
                  "status",
                  "COMPONENT"));
          continue;
        }
        String target = currentIds.get(legacyInstalled.get(legacy));
        if (!"MANAGED_ROLE".equals(item.path("configurationPolicy").asString()) || target == null)
          blockers.add("UNMAPPED_LEGACY_INTERFACE:" + legacy);
        else
          exactSources.add(
              Map.of(
                  "legacyIdentity",
                  legacy,
                  "resourceId",
                  target,
                  "status",
                  "EXACT",
                  "method",
                  item.path("httpMethod").asString(),
                  "path",
                  path,
                  "handler",
                  item.path("handler").asString(),
                  "checkedCode",
                  item.path("permissionCode").asString(),
                  "conditions",
                  item.path("conditions").toString()));
      }
    }
    return resources;
  }

  private String legacyIdentity(
      String context,
      String source,
      String method,
      String path,
      String handler,
      Map<String, List<String>> conditions,
      String discoveredCode) {
    Map<String, List<String>> normalized = new TreeMap<>();
    conditions.forEach(
        (key, values) -> {
          var safe =
              values.stream()
                  .filter(v -> !key.equals("version") || !v.isBlank())
                  .map(v -> Set.of("headers", "params").contains(key) ? safeExpression(v) : v)
                  .distinct()
                  .sorted()
                  .toList();
          if (!safe.isEmpty()) normalized.put(key, safe);
        });
    String route = path.trim().replaceAll("/{2,}", "/");
    if (route.length() > 1 && route.endsWith("/")) route = route.substring(0, route.length() - 1);
    return digest(
        json.writeValueAsString(
            List.of(context, source, method, route, handler, normalized, discoveredCode)));
  }

  private static String safeExpression(String expression) {
    int equals = expression.indexOf('=');
    String name = equals < 0 ? expression : expression.substring(0, equals);
    return name.toLowerCase(Locale.ROOT)
            .matches(".*(authorization|cookie|token|secret|password|api.?key).*")
        ? name + "=<redacted>"
        : expression;
  }

  private static String managementCode(Domain domain, String method) {
    return (domain.kind().equals("PLATFORM") ? "platform.capabilities." : "enterprise.roles.")
        + (method.equals("GET") ? "read" : "write");
  }

  private static Set<String> managementDomains(Map<String, Object> row) {
    if ("ENTERPRISE".equals(row.get("scope")) && row.get("enterprise_id") != null)
      return Set.of("ENTERPRISE:" + row.get("enterprise_id"));
    if ("PLATFORM".equals(row.get("scope")) && row.get("enterprise_id") == null)
      return Set.of("PLATFORM:platform");
    return Set.of();
  }

  private static String annotationCode(HandlerMethod handler) {
    String annotationType = "com.abluepoint.summer.sample.resource.ResourcePermission";
    var annotation =
        AnnotatedElementUtils.findMergedAnnotationAttributes(
            handler.getMethod(), annotationType, false, false);
    if (annotation == null)
      annotation =
          AnnotatedElementUtils.findMergedAnnotationAttributes(
              handler.getBeanType(), annotationType, false, false);
    if (annotation != null && !annotation.getString("value").isBlank())
      return annotation.getString("value").trim();
    return null;
  }

  private static String checkedCode(HandlerMethod handler, String method, String path) {
    String code = annotationCode(handler);
    if (code != null && !code.isBlank()) return code.trim();
    String normalized = path.trim().replaceAll("/{2,}", "/");
    if (normalized.length() > 1 && normalized.endsWith("/"))
      normalized = normalized.substring(0, normalized.length() - 1);
    return method + ":" + normalized;
  }

  private void menus(Map<String, Object> raw, List<Resource> resources, List<String> blockers) {
    var publication = table(raw, "menuPublication");
    if (publication.size() != 1) {
      blockers.add("MISSING_MENU_PUBLICATION");
      return;
    }
    long version = ((Number) publication.getFirst().get("manifest_version")).longValue();
    var effective = new TreeMap<String, Map<String, Object>>();
    for (var row : table(raw, "menus"))
      if (((Number) row.get("manifest_version")).longValue() == version) {
        var menu = new TreeMap<String, Object>(row);
        menu.put("enabled", true);
        for (var override : table(raw, "menuOverrides"))
          if (row.get("menu_id").equals(override.get("menu_id")))
            for (String field :
                List.of("capability_code", "parent_menu_id", "enabled", "sort_order"))
              if (override.get(field) != null) menu.put(field, override.get(field));
        effective.put(text(menu, "menu_id"), menu);
      }
    for (var entry : effective.entrySet()) {
      String id = entry.getKey();
      var menu = entry.getValue();
      var ancestors = new ArrayList<String>();
      String parent = nullable(menu, "parent_menu_id");
      while (parent != null && !parent.isBlank()) {
        if (!effective.containsKey(parent) || ancestors.contains(parent) || parent.equals(id)) {
          blockers.add("UNMAPPED_MENU_ANCESTOR:" + id);
          break;
        }
        ancestors.add(parent);
        parent = nullable(effective.get(parent), "parent_menu_id");
      }
      if (Set.of("customer", "customer/drawer", "customer/modal", "customer/page").contains(id))
        blockers.add("SCOPE_DEPENDENT_MENU:" + id);
      resources.add(
          new Resource(
              id,
              "MENU",
              nullable(menu, "capability_code"),
              "AUTHORIZED",
              Boolean.TRUE.equals(menu.get("enabled")),
              digest(json.writeValueAsString(menu)),
              ancestors));
    }
  }

  private void validateExport(Domain domain, RuntimeExport exported, List<String> blockers) {
    if (exported.capabilities().isEmpty() && exported.bundles().isEmpty()) return;
    try {
      var evidence = json.readTree(exported.evidenceJson());
      if (!domain.id().equals(evidence.path("enterpriseId").asString())
          || !"authenticated-old-http".equals(evidence.path("source").asString())) {
        blockers.add("UNTRUSTED_RUNTIME_EXPORT_PROVENANCE");
        return;
      }
      for (var capability : exported.capabilities()) {
        boolean found = false;
        for (var catalog : evidence.path("catalogs"))
          for (var row : catalog.path("capabilities"))
            if (capability.id().equals(row.path("capabilityId").asString())
                && capability.code().equals(row.path("code").asString())
                && capability.resource().equals(row.path("resource").asString())
                && capability.active() == "ACTIVE".equals(row.path("status").asString()))
              found = true;
        if (!found) blockers.add("UNPROVEN_RUNTIME_CAPABILITY:" + capability.id());
      }
      for (var bundle : exported.bundles()) {
        String history = evidence.path("bundleHistories").path(bundle.id()).asString("");
        boolean found = false;
        if (bundle.historyFingerprint().equals(digest(history)))
          for (var version : json.readTree(history)) {
            Set<String> ids = new TreeSet<>();
            version.path("capabilityIds").forEach(id -> ids.add(id.asString()));
            if (bundle.id().equals(version.path("bundleId").asString())
                && bundle.effectiveVersion() == version.path("version").asLong()
                && bundle.capabilityIds().equals(ids)) found = true;
          }
        if (!found)
          blockers.add("UNPROVEN_BUNDLE_HISTORY:" + bundle.id() + ":" + bundle.effectiveVersion());
      }
    } catch (RuntimeException invalid) {
      blockers.add("INVALID_RUNTIME_EXPORT");
    }
  }

  private static Object ordered(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> normalized = new TreeMap<>();
      map.forEach((key, item) -> normalized.put(key.toString(), ordered(item)));
      return normalized;
    }
    if (value instanceof List<?> list)
      return list.stream().map(LegacyAuthorizationReader::ordered).toList();
    return value;
  }

  private List<Map<String, Object>> rows(String sql, Object... args) {
    return db.queryForList(sql, args).stream()
        .map(
            row -> {
              Map<String, Object> normalized = new TreeMap<>();
              row.forEach(
                  (k, v) -> normalized.put(k, v instanceof java.sql.Timestamp ? v.toString() : v));
              return normalized;
            })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> table(Map<String, Object> raw, String key) {
    return (List<Map<String, Object>>) raw.get(key);
  }

  private static String text(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }

  private static String nullable(Map<String, Object> row, String key) {
    return (String) row.get(key);
  }

  private static Set<String> csv(String value) {
    return value.isBlank() ? Set.of() : new TreeSet<>(Arrays.asList(value.split("\n")));
  }

  private Set<String> stringSet(String value) {
    return new TreeSet<>(Arrays.asList(json.readValue(value, String[].class)));
  }
}
