package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/** Formal HTTP/JPA/JDBC consumer contract. Native MySQL/Redis tests are separate. */
@SpringBootTest(properties="summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme,PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConfigurationDataHttpTest {
  private static final String BASE = "/api/v1/enterprises/ent-acme";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired com.abluepoint.summer.sample.resource.BusinessResourceAccess business;

  private RequestPostProcessor actor() {
    return jwt().jwt(token -> token.subject("summer-user")
        .claim("scope", "system.parameter.read system.parameter.create system.parameter.update system.parameter.publish system.parameter.disable enterprise.statistics.read")
        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
  }

  @Test void fixedPublicParameterRetainsItsPublicFlagAndCurrentPublicDataRange() throws Exception {
    bindBusinessInterfaces();
    bindPublicParameterInterfaces();
    data("create", "SELF");
    mvc.perform(post(BASE + "/system-parameters").with(actor()).with(csrf()).contentType("application/json")
        .content("{\"key\":\"scope10.public\",\"type\":\"STRING\",\"value\":\"public-value\",\"public\":true}"))
        .andExpect(status().isCreated());
    String publicPath = "/public/enterprises/ent-acme/system-parameters/scope10.public";
    mvc.perform(get(publicPath)).andExpect(status().isNotFound());
    String readPermission = data("read", "TENANT_ALL");
    mvc.perform(get(publicPath))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("public-value"));
    mvc.perform(get(publicPath).header("Authorization", "Bearer invalid-public-parameter-token"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/public/system-parameters/scope10.public"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/public/system-parameters/scope10.public").header("X-Enterprise-Id", "ent-acme"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("public-value"));
    mvc.perform(get(publicPath).with(jwt().jwt(token -> token.subject("summer-user")
        .claim("scope", "system.parameter.create")
        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_system.parameter.read"))))
        .andExpect(status().isForbidden());
    mvc.perform(post(BASE + "/system-parameters").with(actor()).with(csrf()).contentType("application/json")
        .content("{\"key\":\"scope10.private\",\"type\":\"STRING\",\"value\":\"private-value\",\"public\":false}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/public/enterprises/ent-acme/system-parameters/scope10.private"))
        .andExpect(status().isNotFound());
    data("update", "SELF");
    mvc.perform(post(BASE + "/system-parameters/scope10.public/disable").with(actor()).with(csrf().asHeader()))
        .andExpect(status().isOk());
    mvc.perform(get(publicPath)).andExpect(status().isNotFound());
    mvc.perform(post(BASE + "/system-parameters/scope10.public/enable").with(actor()).with(csrf().asHeader()))
        .andExpect(status().isOk());
    mvc.perform(get(publicPath)).andExpect(status().isOk());
    var beforeDelete = mvc.perform(get(BASE + "/permissions/" + readPermission).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(delete(BASE + "/permissions/" + readPermission).with(user("summer-user")).with(csrf())
        .header("If-Match", beforeDelete.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    mvc.perform(get(publicPath)).andExpect(status().isNotFound());
  }

  @Test void browserFixturePublishesCanonicalPagesAndBindsTheEnterpriseMenu() throws Exception {
    var fixture = json.readTree(getClass().getResourceAsStream("/fixtures/permission10-business-menu.json"));
    String manifestPath = "/api/v1/platform/authorization/menu-manifest";
    var current = mvc.perform(get(manifestPath).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    long version = json.readTree(current.getResponse().getContentAsString()).path("data").path("manifestVersion").asLong();
    var body = json.createObjectNode();
    body.put("manifestVersion", version + 1); body.set("items", fixture.path("items"));
    var published = mvc.perform(put(manifestPath).with(user("summer-user")).with(csrf().asHeader())
        .header("If-Match", current.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(body)))
        .andExpect(status().isOk()).andReturn();
    var actualItems = json.readTree(published.getResponse().getContentAsString()).path("data").path("items");
    var selected = new java.util.ArrayList<String>();
    for (var wanted : fixture.path("selectedPageIds")) {
      String id = wanted.asText();
      assertTrue(java.util.stream.StreamSupport.stream(actualItems.spliterator(), false)
          .anyMatch(item -> id.equals(item.path("menuId").asText()) && ("/" + id).equals(item.path("path").asText())));
      selected.add(id);
    }
    // Existing management MENU identities retain AUTHORIZED mode; no conflicting PUBLIC grant.
    String permission = permission(Map.of("type", "MENU", "accessMode", "AUTHORIZED"));
    String bindingPath = BASE + "/permissions/" + permission + "/menus";
    var binding = mvc.perform(get(bindingPath).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(put(bindingPath).with(user("summer-user")).with(csrf().asHeader())
        .header("If-Match", binding.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("menuIds", selected))))
        .andExpect(status().isOk());
    var role = mvc.perform(post(BASE + "/roles").with(user("summer-user")).with(csrf().asHeader())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("name", "Browser pages", "code", "browser10-pages", "permissionIds", List.of(permission)))))
        .andExpect(status().isCreated()).andReturn();
    String roleId = json.readTree(role.getResponse().getContentAsString()).path("data").path("roleId").asText();
    String userRoles = BASE + "/users/summer-user/roles";
    var before = mvc.perform(get(userRoles).with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    var roleIds = new java.util.ArrayList<String>();
    for (var id : json.readTree(before.getResponse().getContentAsString()).path("data").path("roleIds")) roleIds.add(id.asText());
    roleIds.add(roleId);
    mvc.perform(put(userRoles).with(user("summer-user")).with(csrf().asHeader())
        .header("If-Match", before.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("roleIds", roleIds))))
        .andExpect(status().isOk());
    var navigation = mvc.perform(get(BASE + "/authorization/navigation").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    var visible = json.readTree(navigation.getResponse().getContentAsString()).path("data").path("items");
    for (String id : selected) assertTrue(java.util.stream.StreamSupport.stream(visible.spliterator(), false)
        .anyMatch(item -> id.equals(item.path("menuId").asText()) && item.path("pageEligible").asBoolean()));
    assertTrue(java.util.stream.StreamSupport.stream(visible.spliterator(), false)
        .anyMatch(item -> "dictionary".equals(item.path("menuId").asText()) && item.path("navOnly").asBoolean()));
  }

  @Test void interfaceGrantDoesNotGrantDataAndCreateReadUpdateUseIndependentActionRanges() throws Exception {
    bindBusinessInterfaces();
    mvc.perform(get(BASE + "/statistics").with(actor())).andExpect(status().isOk());
    var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
    var rawJwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("qualification10")
        .header("alg", "none").subject("summer-user").claim("scope", "system.parameter.read").build();
    var rawActor = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(rawJwt);
    assertTrue(business.requireHttp(domain, rawActor, "GET", "/api/v1/enterprises/{enterpriseId}/system-parameters", "system.parameter.read"));
    var claimOnly = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("converted-authority10")
        .header("alg", "none").subject("summer-user").build();
    var convertedActor = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(claimOnly,
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_system.parameter.read")));
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> business.requireHttp(domain, convertedActor, "GET", "/api/v1/enterprises/{enterpriseId}/system-parameters", "system.parameter.read"));
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> business.requireHttp(domain, rawActor, "GET", "/uninstalled10", "system.parameter.read"));
    var enterpriseCatalog = mvc.perform(get(BASE + "/data-resources").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    assertTrue(enterpriseCatalog.getResponse().getContentAsString().contains("system-parameters"));
    var platformCatalog = mvc.perform(get("/api/v1/platform/data-resources").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    assertFalse(platformCatalog.getResponse().getContentAsString().contains("system-parameters"));
    var group = mvc.perform(post("/api/v1/platform/permission-groups").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"name\":\"Platform resource rejection\"}")).andExpect(status().isCreated()).andReturn();
    mvc.perform(post("/api/v1/platform/permissions").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("code", UUID.randomUUID().toString(), "name", "Wrong domain",
            "groupId", json.readTree(group.getResponse().getContentAsString()).path("data").path("groupId").asText(),
            "type", "DATA", "accessMode", "PUBLIC", "dataScope", Map.of("dataResourceKey", "system-parameters", "action", "read", "scopePreset", "TENANT_ALL")))))
        .andExpect(status().isBadRequest());
    // Interface-only callers see no DATA rows and cannot create a candidate.
    mvc.perform(get(BASE + "/system-parameters").with(actor())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(post(BASE + "/system-parameters").with(actor()).with(csrf()).contentType("application/json")
        .content("{\"key\":\"scope10.denied\",\"type\":\"STRING\",\"value\":\"before\"}"))
        .andExpect(status().isForbidden());
    assertEquals(0, db.queryForObject("select count(*) from summer_system_parameter where parameter_key='scope10.denied'", Integer.class));
    data("create", "SELF");
    var created = mvc.perform(post(BASE + "/system-parameters").with(actor()).with(csrf()).contentType("application/json")
        .content("{\"key\":\"scope10.mine\",\"type\":\"STRING\",\"value\":\"before\"}"))
        .andExpect(status().isCreated()).andReturn();
    long version = json.readTree(created.getResponse().getContentAsString()).path("data").path("version").asLong();
    mvc.perform(get(BASE + "/system-parameter-events").with(actor())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    String eventId = db.queryForObject("select event_id from summer_system_parameter_outbox where parameter_key='scope10.mine'", String.class);
    db.update("update summer_system_parameter_outbox set status='DEAD_LETTER',attempts=2 where event_id=?",eventId);
    mvc.perform(post(BASE + "/system-parameter-events/" + eventId + "/retry").with(actor()).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("DEAD_LETTER",db.queryForObject("select status from summer_system_parameter_outbox where event_id=?",String.class,eventId));
    assertEquals("summer-user", db.queryForObject("select owner_id from summer_system_parameter where parameter_key='scope10.mine'", String.class));
    mvc.perform(get(BASE + "/system-parameters").with(actor())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    String readPermission = data("read", "SELF");
    String recordId = db.queryForObject("select id from summer_system_parameter where parameter_key='scope10.mine'", String.class);
    mvc.perform(post(BASE + "/data-resources/system-parameters/preview").with(user("summer-user")).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("subject", "summer-user", "recordId", recordId, "action", "read"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(true));
    mvc.perform(post("/api/v1/platform/data-resources/system-parameters/preview").with(user("summer-user")).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("subject", "summer-user", "recordId", recordId, "action", "read"))))
        .andExpect(status().isBadRequest());
    mvc.perform(get(BASE + "/system-parameters").with(actor())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    String body = json.writeValueAsString(Map.of("version", version, "value", "changed"));
    mvc.perform(patch(BASE + "/system-parameters/scope10.mine").with(actor()).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isNotFound());
    assertEquals("\"before\"", db.queryForObject("select value_json from summer_system_parameter where parameter_key='scope10.mine'", String.class));
    long historyBefore = db.queryForObject("select count(*) from summer_system_parameter_version where parameter_key='scope10.mine'", Long.class);
    mvc.perform(post(BASE + "/system-parameters/scope10.mine/draft").with(actor()).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("baseVersion", version,
            "value", "release", "idempotencyKey", "scope10-denied-draft"))))
        .andExpect(status().isNotFound());
    assertEquals(historyBefore, db.queryForObject("select count(*) from summer_system_parameter_version where parameter_key='scope10.mine'", Long.class));
    data("update", "SELF");
    mvc.perform(post(BASE + "/system-parameter-events/" + eventId + "/retry").with(actor()).with(csrf()))
        .andExpect(status().isOk());
    assertEquals("PENDING",db.queryForObject("select status from summer_system_parameter_outbox where event_id=?",String.class,eventId));
    mvc.perform(patch(BASE + "/system-parameters/scope10.mine").with(actor()).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isOk());
    assertEquals("\"changed\"", db.queryForObject("select value_json from summer_system_parameter where parameter_key='scope10.mine'", String.class));
    db.update("insert into summer_configuration_override(id,tenant_id,resource,config_key,scope_id,created_by,owner_id,value_json,public_readable,enabled,version,latest_revision,updated_at) values('scope10-other-override','ent-acme','system-parameters','scope10.mine','_tenant','other','other','\"old\"',false,true,0,0,CURRENT_TIMESTAMP)");
    mvc.perform(post(BASE + "/system-parameters/scope10.mine/overrides/_tenant/draft").with(actor()).with(csrf())
        .contentType("application/json").content("{\"value\":\"hidden-change\",\"expectedVersion\":0}"))
        .andExpect(status().isNotFound());
    assertEquals("\"old\"", db.queryForObject("select value_json from summer_configuration_override where id='scope10-other-override'", String.class));
    assertEquals(0, db.queryForObject("select count(*) from summer_configuration_override_revision where override_id='scope10-other-override'", Integer.class));
    db.update("delete from summer_configuration_override where id='scope10-other-override'");
    long currentVersion = db.queryForObject("select version from summer_system_parameter where parameter_key='scope10.mine'", Long.class);
    var draft = mvc.perform(post(BASE + "/system-parameters/scope10.mine/draft").with(actor()).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("baseVersion", currentVersion,
            "value", "release", "idempotencyKey", "scope10-draft"))))
        .andExpect(status().isCreated()).andReturn();
    long revision = json.readTree(draft.getResponse().getContentAsString()).path("data").path("revision").asLong();
    mvc.perform(post(BASE + "/system-parameters/scope10.mine/publish").with(actor()).with(csrf())
        .contentType("application/json").content(json.writeValueAsString(Map.of("expectedVersion", currentVersion,
            "revision", revision, "idempotencyKey", "scope10-publish"))))
        .andExpect(status().isOk());
    assertEquals("\"release\"", db.queryForObject("select value_json from summer_system_parameter where parameter_key='scope10.mine'", String.class));
    assertEquals(currentVersion + 1, db.queryForObject("select version from summer_system_parameter where parameter_key='scope10.mine'", Long.class));
    var beforeDelete = mvc.perform(get(BASE + "/permissions/" + readPermission).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(delete(BASE + "/permissions/" + readPermission).with(user("summer-user")).with(csrf())
        .header("If-Match", beforeDelete.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
    mvc.perform(get(BASE + "/system-parameters").with(actor())).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    // A write consumes its own action range and must not accidentally require a READ grant.
    mvc.perform(post(BASE + "/system-parameters/scope10.mine/overrides/_tenant/draft").with(actor()).with(csrf())
        .contentType("application/json").content("{\"value\":\"first override\"}"))
        .andExpect(status().isOk());
    assertEquals("summer-user", db.queryForObject("select owner_id from summer_configuration_override where config_key='scope10.mine'", String.class));
    mvc.perform(post(BASE + "/system-parameters/scope10.mine/overrides/_tenant/draft").with(actor()).with(csrf())
        .contentType("application/json").content("{\"value\":\"second override\",\"expectedVersion\":0}"))
        .andExpect(status().isOk());
    assertEquals(1L, db.queryForObject("select version from summer_configuration_override where config_key='scope10.mine'", Long.class));
    assertEquals(2, db.queryForObject("select count(*) from summer_configuration_override_revision where override_id=(select id from summer_configuration_override where config_key='scope10.mine')", Integer.class));


  }

  private String permission(Map<String, Object> extra) throws Exception {
    var group = mvc.perform(post(BASE + "/permission-groups").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"name\":\"Configuration10\"}")).andExpect(status().isCreated()).andReturn();
    var body = new java.util.HashMap<String, Object>(extra);
    body.put("code", UUID.randomUUID().toString()); body.put("name", "Configuration10"); body.putIfAbsent("accessMode", "PUBLIC");
    body.put("groupId", json.readTree(group.getResponse().getContentAsString()).path("data").path("groupId").asText());
    var result = mvc.perform(post(BASE + "/permissions").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data").path("permissionId").asText();
  }

  private String data(String action, String preset) throws Exception {
    return permission(Map.of("type", "DATA", "dataScope", Map.of("dataResourceKey", "system-parameters", "action", action, "scopePreset", preset)));
  }

  private void bindBusinessInterfaces() throws Exception {
    var ids = new java.util.ArrayList<String>();
    for (int page = 0; page < 10; page++) {
      var response = mvc.perform(get(BASE + "/authorization/interface-directory").param("size", "200").param("page", "" + page)
          .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
      var data = json.readTree(response.getResponse().getContentAsString()).path("data");
      for (var row : data.path("items"))
        if (row.path("normalizedPath").asText().startsWith("/api/v1/enterprises/{enterpriseId}/system-parameters") || row.path("normalizedPath").asText().equals("/api/v1/enterprises/{enterpriseId}/statistics") || row.path("normalizedPath").asText().startsWith("/api/v1/enterprises/{enterpriseId}/system-parameter-events")) ids.add(row.path("resourceId").asText());
      if ((page + 1) * 200 >= data.path("total").asInt()) break;
    }
    assertFalse(ids.isEmpty());
    bindInterfaces(ids);
  }

  private void bindPublicParameterInterfaces() throws Exception {
    var paths = java.util.Set.of("/public/system-parameters/{key}",
        "/public/enterprises/{enterpriseId}/system-parameters/{key}");
    var found = new java.util.HashSet<String>();
    var ids = new java.util.ArrayList<String>();
    for (int page = 0; page < 10; page++) {
      var response = mvc.perform(get(BASE + "/authorization/interface-directory").param("size", "200").param("page", "" + page)
          .with(user("summer-user"))).andExpect(status().isOk()).andReturn();
      var data = json.readTree(response.getResponse().getContentAsString()).path("data");
      for (var row : data.path("items")) {
        String path = row.path("normalizedPath").asText();
        if (paths.contains(path) && "GET".equals(row.path("httpMethod").asText())) {
          found.add(path);
          ids.add(row.path("resourceId").asText());
        }
      }
      if ((page + 1) * 200 >= data.path("total").asInt()) break;
    }
    assertEquals(paths, found);
    assertEquals(2, ids.size());
    bindInterfaces(ids);
  }

  private void bindInterfaces(java.util.List<String> ids) throws Exception {
    String id = permission(Map.of("type", "INTERFACE"));
    var collection = mvc.perform(get(BASE + "/permissions/" + id + "/interfaces").with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    mvc.perform(put(BASE + "/permissions/" + id + "/interfaces").with(user("summer-user")).with(csrf())
        .header("If-Match", collection.getResponse().getHeader("ETag"))
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("resourceIds", ids)))).andExpect(status().isOk());
  }

  @Test
  void oneMenuPermissionAloneQualifiesMultiplePagesAndRevokingItRemovesBoth() throws Exception {
    String subject = "menu-causal-" + UUID.randomUUID();
    String parent = "menu-parent-" + UUID.randomUUID();
    var pages = List.of("menu-a-" + UUID.randomUUID(), "menu-b-" + UUID.randomUUID());
    causalIdentity(subject);
    causalPublish(parent, pages);
    for (String page : pages) causalAssociations(page, java.util.Set.of());
    var before = causalNavigation(subject, List.of());
    for (String page : pages) causalPage(before, page, false);

    String permission = causalPermission(pages);
    String role = causalRole(permission);
    causalAssign(subject, List.of(role));
    var granted = causalNavigation(subject, List.of(role));
    for (String page : pages) {
      causalAssociations(page, java.util.Set.of(permission));
      causalPage(granted, page, true);
    }
    causalDirectory(granted, parent);

    // Keep the identity, navigation entry point and user-role binding unchanged.
    causalReplace(BASE + "/roles/" + role + "/permissions", Map.of("permissionIds", List.of()));
    assertEquals(0, db.queryForObject("select count(*) from summer_role_permission where domain_key=? and role_id=?",
        Integer.class, "ENTERPRISE:ent-acme", role));
    var revoked = causalNavigation(subject, List.of(role));
    for (String page : pages) {
      causalAssociations(page, java.util.Set.of(permission));
      causalPage(revoked, page, false);
    }
  }

  private tools.jackson.databind.JsonNode causalCreate(String path, Map<String, Object> body) throws Exception {
    var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .with(user("summer-user"))
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsString(body)))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private tools.jackson.databind.JsonNode causalReplace(String path, Map<String, Object> body) throws Exception {
    var before = mvc.perform(get(path).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    assertNotNull(before.getResponse().getHeader("ETag"));
    var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
            .with(user("summer-user"))
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .header("If-Match", before.getResponse().getHeader("ETag"))
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsString(body)))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("data");
  }

  private void causalPublish(String parent, java.util.List<String> children) throws Exception {
    String path = "/api/v1/platform/authorization/menu-manifest";
    var before = mvc.perform(get(path).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn();
    var current = json.readTree(before.getResponse().getContentAsString()).path("data");
    var items = json.createArrayNode();
    // Re-publish only supported metadata, preserving the existing catalog.
    for (var old : current.path("items")) {
      var item = json.createObjectNode();
      for (String field : java.util.List.of("menuId", "parentMenuId", "path", "titleKey", "iconKey", "sortOrder"))
        if (old.has(field)) item.set(field, old.get(field));
      items.add(item);
    }
    var directory = json.createObjectNode();
    directory.put("menuId", parent); directory.put("path", "/" + parent);
    directory.put("titleKey", "Causal directory"); directory.put("iconKey", ""); directory.put("sortOrder", 900);
    items.add(directory);
    for (String child : children) {
      var item = json.createObjectNode();
      item.put("menuId", child); item.put("parentMenuId", parent); item.put("path", "/" + child);
      item.put("titleKey", "Causal page"); item.put("iconKey", ""); item.put("sortOrder", 901);
      items.add(item);
    }
    causalReplace(path, Map.of("manifestVersion", current.path("manifestVersion").asLong() + 1, "items", items));
  }

  private void causalIdentity(String subject) {
    assertEquals(0, db.queryForObject("select count(*) from summer_auth_user where username=?", Integer.class, subject));
    db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) values (?,'!unusable-menu-fixture-login','USER',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", subject);
    assertEquals(1, db.queryForObject("select count(*) from summer_enterprise where enterprise_id='ent-acme' and status='ACTIVE'", Integer.class));
    db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values ('ent-acme',?,?,?,?,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", java.util.UUID.randomUUID().toString(), subject, subject, subject + "@example.test");
  }

  private String causalPermission(java.util.List<String> menus) throws Exception {
    String group = causalCreate(BASE + "/permission-groups", Map.of("name", "Causal menus")).path("groupId").asText();
    String permission = causalCreate(BASE + "/permissions", Map.of(
        "name", "Causal MENU", "code", java.util.UUID.randomUUID().toString(), "groupId", group,
        "type", "MENU", "accessMode", "AUTHORIZED", "status", "ACTIVE")).path("permissionId").asText();
    var bound = causalReplace(BASE + "/permissions/" + permission + "/menus", Map.of("menuIds", menus));
    var actual = new java.util.HashSet<String>();
    for (var id : bound.path("resourceIds")) actual.add(id.asText());
    assertEquals(new java.util.HashSet<>(menus), actual);
    assertEquals(new java.util.HashSet<>(menus), new java.util.HashSet<>(db.queryForList(
        "select resource_id from summer_permission_resource where domain_key=? and permission_id=? and resource_type='MENU'",
        String.class, "ENTERPRISE:ent-acme", permission)));
    return permission;
  }

  private String causalRole(String permission) throws Exception {
    String role = causalCreate(BASE + "/roles", Map.of("name", "Causal role",
        "code", java.util.UUID.randomUUID().toString(), "permissionIds", java.util.List.of(permission))).path("roleId").asText();
    assertEquals(java.util.List.of(permission), db.queryForList(
        "select permission_id from summer_role_permission where domain_key=? and role_id=?",
        String.class, "ENTERPRISE:ent-acme", role));
    return role;
  }

  private void causalAssign(String subject, java.util.List<String> roles) throws Exception {
    var result = causalReplace(BASE + "/users/" + subject + "/roles", Map.of("roleIds", roles));
    var actual = new java.util.HashSet<String>();
    for (var role : result.path("roleIds")) actual.add(role.asText());
    assertEquals(new java.util.HashSet<>(roles), actual);
  }

  private tools.jackson.databind.JsonNode causalNavigation(String subject, java.util.List<String> roles) throws Exception {
    assertEquals(1, db.queryForObject("select count(*) from summer_auth_user where username=? and active=true", Integer.class, subject));
    assertEquals(1, db.queryForObject("select count(*) from summer_enterprise_member m join summer_enterprise e on e.enterprise_id=m.enterprise_id where m.enterprise_id='ent-acme' and m.subject=? and m.status='ACTIVE' and e.status='ACTIVE'", Integer.class, subject));
    assertEquals(new java.util.HashSet<>(roles), new java.util.HashSet<>(db.queryForList(
        "select role_id from summer_permission_user_role where domain_key=? and username=?", String.class, "ENTERPRISE:ent-acme", subject)));
    var result = mvc.perform(get(BASE + "/authorization/navigation").with(user(subject)))
        .andExpect(status().isOk()).andReturn();
    var response = json.readTree(result.getResponse().getContentAsString());
    assertTrue(response.path("success").asBoolean());
    var items = response.path("data").path("items");
    assertTrue(items.isArray());
    return items;
  }

  private void causalPage(tools.jackson.databind.JsonNode items, String id, boolean eligible) {
    var matches = java.util.stream.StreamSupport.stream(items.spliterator(), false)
        .filter(item -> id.equals(item.path("menuId").asText())).toList();
    assertTrue(matches.size() <= 1);
    for (var item : matches) assertTrue(item.path("pageEligible").isBoolean());
    assertEquals(eligible, matches.stream().anyMatch(item -> item.path("pageEligible").asBoolean()), id);
    if (eligible) assertFalse(matches.getFirst().path("navOnly").asBoolean());
  }

  private void causalDirectory(tools.jackson.databind.JsonNode items, String parent) {
    var directory = java.util.stream.StreamSupport.stream(items.spliterator(), false)
        .filter(item -> parent.equals(item.path("menuId").asText())).findFirst().orElseThrow();
    assertTrue(directory.path("navOnly").asBoolean());
    assertFalse(directory.path("pageEligible").asBoolean());
    assertEquals(0, directory.path("actions").size());
  }

  private void causalAssociations(String menu, java.util.Set<String> permissions) {
    assertEquals(permissions, new java.util.HashSet<>(db.queryForList(
        "select permission_id from summer_permission_resource where domain_key=? and resource_type='MENU' and resource_id=?",
        String.class, "ENTERPRISE:ent-acme", menu)));
    assertEquals(permissions.size(), db.queryForObject(
        "select count(*) from summer_permission_resource x join summer_permission p on p.domain_key=x.domain_key and p.permission_id=x.permission_id where x.domain_key=? and x.resource_type='MENU' and x.resource_id=? and p.type='MENU' and p.access_mode='AUTHORIZED' and p.status='ACTIVE'",
        Integer.class, "ENTERPRISE:ent-acme", menu));
  }
}
