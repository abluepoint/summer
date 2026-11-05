package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({
  RolePermissionHttpTest.Schema.class,
  com.example.catalog.ManagedInterfaceFixture.class,
  com.example.catalog.ManagedInterfaceExtensionFixture.class
})
class InterfaceBindingsHttpTest {
  @org.springframework.beans.factory.annotation.Value("${local.server.port}")
  int port;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired org.springframework.context.ApplicationContext context;
  @Autowired tools.jackson.databind.ObjectMapper json;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

  @Autowired com.example.catalog.ManagedInterfaceFixture fixture;

  private String permission(String mode) throws Exception {
    String group =
        db.queryForObject(
            "select group_id from summer_permission where domain_key='PLATFORM:platform' and"
                + " code='PUBLIC'",
            String.class);
    var response =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/platform/permissions")
                    .with(user("summer-user"))
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of(
                                "code",
                                java.util.UUID.randomUUID().toString(),
                                "name",
                                "Interface fixture",
                                "type",
                                "INTERFACE",
                                "accessMode",
                                mode,
                                "groupId",
                                group))))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString())
        .path("data")
        .path("permissionId")
        .asText();
  }

  private org.springframework.test.web.servlet.ResultActions bind(
      String id, String resource, long version) throws Exception {
    return mvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/platform/permissions/" + id + "/interfaces")
            .with(user("summer-user"))
            .with(
                org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.csrf())
            .header("If-Match", "\"" + version + "\"")
            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    java.util.Map.of("resourceIds", java.util.List.of(resource)))));
  }

  @Test
  void readsAnIndependentEmptyAssociationCollection() throws Exception {
    String group =
        db.queryForObject(
            "select group_id from summer_permission where domain_key='PLATFORM:platform' and"
                + " code='PUBLIC'",
            String.class);
    var created =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/platform/permissions")
                    .with(user("summer-user"))
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content(
                        "{\"code\":\"interfaces-test-"
                            + java.util.UUID.randomUUID()
                            + "\",\"name\":\"Interface"
                            + " bindings\",\"type\":\"INTERFACE\",\"groupId\":\""
                            + group
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id =
        tools.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(created)
            .path("data")
            .path("permissionId")
            .asText();
    mvc.perform(get("/api/v1/platform/permissions/" + id + "/interfaces").with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.data.resourceIds").isEmpty())
        .andExpect(jsonPath("$.data.version").value(0));
    String route = "/api/v1/platform/permissions/" + id + "/interfaces";
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("If-Match", "\"0\"")
                .header("Idempotency-Key", "unexpected-resource-field")
                .contentType("application/json")
                .content("{\"resourceIds\":[],\"accessMode\":\"PUBLIC\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("Idempotency-Key", "missing-match")
                .contentType("application/json")
                .content("{\"resourceIds\":[]}"))
        .andExpect(status().is(428));
    String key = java.util.UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                  .with(user("summer-user"))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf())
                  .header("If-Match", "\"0\"")
                  .header("Idempotency-Key", key)
                  .contentType("application/json")
                  .content("{\"resourceIds\":[]}"))
          .andExpect(status().isOk())
          .andExpect(header().string("ETag", "\"1\""));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("If-Match", "\"0\"")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"resourceIds\":[]}"))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content("{\"resourceIds\":[]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void menuBindingKeepsFullLengthIdentityWithoutWideningInterfaceIds() throws Exception {
    String menu = "menu-" + "m".repeat(155);
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values (?,?,'Long menu','menu',999,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        menu,
        "/long-menu-04");
    String group =
        db.queryForObject(
            "select group_id from summer_permission where domain_key='PLATFORM:platform' and"
                + " code='PUBLIC'",
            String.class);
    var created =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/platform/permissions")
                    .with(user("summer-user"))
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of(
                                "code",
                                java.util.UUID.randomUUID().toString(),
                                "name",
                                "Long menu binding",
                                "type",
                                "MENU",
                                "groupId",
                                group))))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        json.readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("permissionId")
            .asText();
    String route = "/api/v1/platform/permissions/" + id + "/menus";
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("If-Match", "\"0\"")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(java.util.Map.of("menuIds", java.util.List.of(menu)))))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.data.resourceIds[0]").value(menu));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        java.util.Map.of("menuIds", java.util.List.of(menu + "x")))))
        .andExpect(status().isBadRequest());
    mvc.perform(get(route).with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.data.resourceIds[0]").value(menu));
    String interfacePermission = permission("PUBLIC");
    bind(interfacePermission, "i".repeat(97), 0).andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/platform/permissions/" + interfacePermission + "/interfaces")
                .with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.data.resourceIds").isEmpty());
  }

  @Test
  void actualHttpUsesTheFirstOrderedMappingForOverlappingMvcAndExtension() throws Exception {
    String path = "/api/v1/platform/interface-overlap";
    var response =
        mvc.perform(
                get("/api/v1/platform/authorization/interface-directory")
                    .param("size", "200")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var items =
        json.readTree(response.getResponse().getContentAsString()).path("data").path("items");
    var candidates =
        java.util.stream.StreamSupport.stream(items.spliterator(), false)
            .filter(
                row ->
                    row.path("normalizedPath").asString().equals(path)
                        && row.path("httpMethod").asString().equals("GET"))
            .toList();
    org.junit.jupiter.api.Assertions.assertEquals(2, candidates.size());
    String extension =
        candidates.stream()
            .filter(row -> row.path("source").asString().equals("TEST_URL"))
            .findFirst()
            .orElseThrow()
            .path("resourceId")
            .asString();
    String mvcResource =
        candidates.stream()
            .filter(row -> row.path("source").asString().equals("MVC"))
            .findFirst()
            .orElseThrow()
            .path("resourceId")
            .asString();
    String id = permission("PUBLIC");
    bind(id, extension, 0).andExpect(status().isOk());
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      var uri = java.net.URI.create("http://127.0.0.1:" + port + path);
      var publicExtension =
          client.send(
              java.net.http.HttpRequest.newBuilder(uri).GET().build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      org.junit.jupiter.api.Assertions.assertEquals(
          200, publicExtension.statusCode(), publicExtension.body());
      org.junit.jupiter.api.Assertions.assertTrue(
          json.readTree(publicExtension.body()).path("extension").asBoolean());
      org.junit.jupiter.api.Assertions.assertFalse(
          json.readTree(publicExtension.body()).has("mvc"));
      var invalidBearer =
          client.send(
              java.net.http.HttpRequest.newBuilder(uri)
                  .header("Authorization", "Bearer invalid-overlap-token")
                  .GET()
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      org.junit.jupiter.api.Assertions.assertEquals(401, invalidBearer.statusCode());
      bind(id, mvcResource, 1).andExpect(status().isOk());
      var publicShadowedMvc =
          client.send(
              java.net.http.HttpRequest.newBuilder(uri).GET().build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      org.junit.jupiter.api.Assertions.assertEquals(
          401, publicShadowedMvc.statusCode(), publicShadowedMvc.body());
      bind(id, extension, 2).andExpect(status().isOk());
      var unsupportedExtensionMethod =
          client.send(
              java.net.http.HttpRequest.newBuilder(uri)
                  .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                  .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      org.junit.jupiter.api.Assertions.assertEquals(401, unsupportedExtensionMethod.statusCode());
    }
  }

  @Test
  void listsPersistentConfigurableResourcesWithoutPermissionFields() throws Exception {
    mvc.perform(get("/api/v1/platform/authorization/interface-directory").with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].resourceId").isString())
        .andExpect(jsonPath("$.data.items[0].permissionCode").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].accessMode").doesNotExist());
  }

  @Test
  void actualConditionsAndVariablesUseTheBoundPersistentResource() throws Exception {
    var directory =
        mvc.perform(
                get("/api/v1/platform/authorization/interface-directory")
                    .param("size", "200")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var items =
        json.readTree(directory.getResponse().getContentAsString()).path("data").path("items");
    var resources =
        java.util.stream.StreamSupport.stream(items.spliterator(), false)
            .filter(
                row ->
                    row.path("normalizedPath")
                            .asText()
                            .equals("/api/v1/platform/interface-fixture/{id}")
                        && row.path("httpMethod").asText().equals("GET"))
            .toList();
    org.junit.jupiter.api.Assertions.assertEquals(2, resources.size());
    var first =
        resources.stream()
            .filter(row -> row.path("conditions").path("params").get(0).asText().equals("kind=a"))
            .findFirst()
            .orElseThrow();
    String resource = first.path("resourceId").asText();
    mvc.perform(
            get("/api/v1/platform/interface-fixture/17")
                .param("kind", "a")
                .header("X-Route", "one"))
        .andExpect(status().isUnauthorized());
    String permission = permission("PUBLIC");
    bind(permission, resource, 0).andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/platform/interface-fixture/17")
                .param("kind", "a")
                .header("X-Route", "one"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("17"));
    mvc.perform(
            get("/api/v1/platform/interface-fixture/99")
                .param("kind", "a")
                .header("X-Route", "one"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/platform/interface-fixture/17")
                .param("kind", "b")
                .header("X-Route", "one"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/platform/interface-fixture/17")
                .param("kind", "a")
                .header("X-Route", "one")
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    String conflict = permission("AUTHENTICATED");
    bind(conflict, resource, 0).andExpect(status().isConflict());
    mvc.perform(
            get("/api/v1/platform/authorization/interface-directory")
                .param("size", "200")
                .with(user("summer-user")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/platform/interface-fixture/17")
                .param("kind", "a")
                .header("X-Route", "one"))
        .andExpect(status().isOk());
  }

  private String generationResource() throws Exception {
    var response =
        mvc.perform(
                get("/api/v1/platform/authorization/interface-directory")
                    .param("size", "200")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    return java.util.stream.StreamSupport.stream(
            json.readTree(response.getResponse().getContentAsString())
                .path("data")
                .path("items")
                .spliterator(),
            false)
        .filter(
            row ->
                row.path("normalizedPath").asText().equals("/api/v1/platform/interface-generation")
                    && row.path("httpMethod").asText().equals("GET"))
        .findFirst()
        .orElseThrow()
        .path("resourceId")
        .asText();
  }

  @Test
  void replacementsAndDisappearanceNeverRestoreOldBindings() throws Exception {
    var info =
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths(
                "/api/v1/platform/interface-generation")
            .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
            .options(mappings.getBuilderConfiguration())
            .build();
    var first = fixture.getClass().getMethod("replacementA");
    var second = fixture.getClass().getMethod("replacementB");
    try {
      mappings.registerMapping(info, fixtureBean(), first);
      String initial = generationResource();
      String permission = permission("PUBLIC");
      bind(permission, initial, 0).andExpect(status().isOk());
      mvc.perform(get("/api/v1/platform/interface-generation")).andExpect(status().isOk());
      org.junit.jupiter.api.Assertions.assertEquals(initial, generationResource());
      mappings.unregisterMapping(info);
      mappings.registerMapping(info, fixtureBean(), second);
      String replacement = generationResource();
      org.junit.jupiter.api.Assertions.assertNotEquals(initial, replacement);
      mvc.perform(get("/api/v1/platform/interface-generation"))
          .andExpect(status().isUnauthorized());
      bind(permission, replacement, 1).andExpect(status().isOk());
      mvc.perform(get("/api/v1/platform/interface-generation")).andExpect(status().isOk());
      mappings.unregisterMapping(info);
      mappings.registerMapping(info, fixtureBean(), first);
      String returned = generationResource();
      org.junit.jupiter.api.Assertions.assertNotEquals(initial, returned);
      mvc.perform(get("/api/v1/platform/interface-generation"))
          .andExpect(status().isUnauthorized());
      mappings.unregisterMapping(info);
      mvc.perform(
              get("/api/v1/platform/authorization/interface-directory").with(user("summer-user")))
          .andExpect(status().isOk());
      mappings.registerMapping(info, fixtureBean(), first);
      org.junit.jupiter.api.Assertions.assertNotEquals(returned, generationResource());
      bind(permission, initial, 2).andExpect(status().isBadRequest());
    } finally {
      mappings.unregisterMapping(info);
    }
  }

  @Test
  void explicitNonMvcAdapterUsesTheSameDecisionAndRejectsOtherMethods() throws Exception {
    String path = "/api/v1/platform/interface-extension";
    var response =
        mvc.perform(
                get("/api/v1/platform/authorization/interface-directory")
                    .param("size", "200")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    String resource =
        java.util.stream.StreamSupport.stream(
                json.readTree(response.getResponse().getContentAsString())
                    .path("data")
                    .path("items")
                    .spliterator(),
                false)
            .filter(row -> row.path("normalizedPath").asText().equals(path))
            .findFirst()
            .orElseThrow()
            .path("resourceId")
            .asText();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    String permission = permission("PUBLIC");
    bind(permission, resource, 0).andExpect(status().isOk());
    mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void pathAndMethodChangesRequireExplicitReconnection() throws Exception {
    var options = mappings.getBuilderConfiguration();
    String path = "/api/v1/platform/interface-move";
    var original =
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths(path)
            .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
            .options(options)
            .build();
    var changedMethod =
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths(path)
            .methods(org.springframework.web.bind.annotation.RequestMethod.POST)
            .options(options)
            .build();
    var changedPath =
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths(path + "-new")
            .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
            .options(options)
            .build();
    var method = fixture.getClass().getMethod("replacementA");
    try {
      mappings.registerMapping(original, fixtureBean(), method);
      String first = listedResource(path, "GET");
      String permission = permission("PUBLIC");
      bind(permission, first, 0).andExpect(status().isOk());
      mvc.perform(get(path)).andExpect(status().isOk());
      mappings.unregisterMapping(original);
      mappings.registerMapping(changedMethod, fixtureBean(), method);
      String second = listedResource(path, "POST");
      org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                  .with(user("summer-user"))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf()))
          .andExpect(status().isForbidden());
      bind(permission, second, 1).andExpect(status().isOk());
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                  .with(user("summer-user"))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf()))
          .andExpect(status().isOk());
      mappings.unregisterMapping(changedMethod);
      mappings.registerMapping(changedPath, fixtureBean(), method);
      String third = listedResource(path + "-new", "GET");
      org.junit.jupiter.api.Assertions.assertNotEquals(second, third);
      mvc.perform(get(path + "-new")).andExpect(status().isUnauthorized());
      bind(permission, third, 2).andExpect(status().isOk());
      mvc.perform(get(path + "-new")).andExpect(status().isOk());
    } finally {
      mappings.unregisterMapping(original);
      mappings.unregisterMapping(changedMethod);
      mappings.unregisterMapping(changedPath);
    }
  }

  @Test
  void invitationManagementHasNoApplicantBypassForUnboundResources() throws Exception {
    String permission = db.queryForObject(
        "select permission_id from summer_permission where domain_key='ENTERPRISE:ent-acme' and code='management-read'",
        String.class);
    String target = db.queryForObject(
        "select resource_id from summer_interface_resource where normalized_path='/api/v1/enterprises/{enterpriseId}/invitations' and http_method='GET' and active=true",
        String.class);
    String route = "/api/v1/enterprises/ent-acme/permissions/" + permission + "/interfaces";
    var initial = mvc.perform(get(route).with(user("summer-user")))
        .andExpect(status().isOk()).andReturn().getResponse();
    java.util.List<String> original = new java.util.ArrayList<>();
    json.readTree(initial.getContentAsString()).path("data").path("resourceIds")
        .forEach(id -> original.add(id.asText()));
    org.junit.jupiter.api.Assertions.assertTrue(original.contains(target),
        "Fresh management seed must cover the invitation management entry");
    var changed = replaceInvitationBindings(route,
        original.stream().filter(id -> !target.equals(id)).toList(), initial.getHeader("ETag"));
    try {
    var response =
        mvc.perform(
                get("/api/v1/enterprises/ent-acme/invitations")
                    .with(
                        user("summer-user")
                            .authorities(
                                new org.springframework.security.core.authority
                                    .SimpleGrantedAuthority("SCOPE_enterprise.invitation.read"))))
            .andExpect(status().isForbidden())
            .andReturn();
    org.junit.jupiter.api.Assertions.assertNull(
        response.getHandler(),
        "Unbound management must be rejected before MVC, not deferred to an applicant exemption");
    } finally {
      replaceInvitationBindings(route, original, changed.getHeader("ETag"));
    }
  }

  private org.springframework.mock.web.MockHttpServletResponse replaceInvitationBindings(
      String route, java.util.List<String> ids, String etag) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
        .with(user("summer-user"))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("If-Match", etag)
        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
        .contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("resourceIds", ids))))
        .andExpect(status().isOk()).andReturn().getResponse();
  }

  @Test
  void replacingARegisteredBeanOfTheSameClassRequiresReconnection() throws Exception {
    var info =
        org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths(
                "/api/v1/platform/interface-bean")
            .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
            .options(mappings.getBuilderConfiguration())
            .build();
    String firstBean = fixtureBean();
    var factory =
        (org.springframework.beans.factory.support.DefaultListableBeanFactory)
            context.getAutowireCapableBeanFactory();
    String secondBean = "perm04-replacement-fixture";
    factory.registerSingleton(secondBean, new com.example.catalog.ManagedInterfaceFixture());
    var method = fixture.getClass().getMethod("replacementA");
    try {
      mappings.registerMapping(info, firstBean, method);
      String first = listedResource("/api/v1/platform/interface-bean", "GET");
      String permission = permission("PUBLIC");
      bind(permission, first, 0).andExpect(status().isOk());
      mvc.perform(get("/api/v1/platform/interface-bean")).andExpect(status().isOk());
      mappings.unregisterMapping(info);
      mappings.registerMapping(info, secondBean, method);
      String second = listedResource("/api/v1/platform/interface-bean", "GET");
      org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
      mvc.perform(get("/api/v1/platform/interface-bean")).andExpect(status().isUnauthorized());
      bind(permission, second, 1).andExpect(status().isOk());
      mvc.perform(get("/api/v1/platform/interface-bean")).andExpect(status().isOk());
    } finally {
      mappings.unregisterMapping(info);
      factory.destroySingleton(secondBean);
    }
  }

  private String fixtureBean() {
    return context.getBeanNamesForType(com.example.catalog.ManagedInterfaceFixture.class)[0];
  }

  private String listedResource(String path, String method) throws Exception {
    var response =
        mvc.perform(
                get("/api/v1/platform/authorization/interface-directory")
                    .param("size", "200")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    return java.util.stream.StreamSupport.stream(
            json.readTree(response.getResponse().getContentAsString())
                .path("data")
                .path("items")
                .spliterator(),
            false)
        .filter(
            row ->
                row.path("normalizedPath").asText().equals(path)
                    && row.path("httpMethod").asText().equals(method))
        .findFirst()
        .orElseThrow()
        .path("resourceId")
        .asText();
  }
}
