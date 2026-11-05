package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** HTTP navigation contract; isolated SQL fixtures establish resource hierarchy and role facts. */
@SpringBootTest(
    properties = {
      "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme",
      "menu.contract.test=true"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class MenuNavigationHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  @org.junit.jupiter.api.BeforeEach
  void publicationState() {
    db.update(
        "insert into summer_menu_publication(publication_id,published,manifest_version) values"
            + " ('global',true,8) on duplicate key update published=true,manifest_version=8");
  }

  @Test
  void enabledUnauthorizedParentIsDirectory() throws Exception {
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values"
            + " ('roles',null,'/roles','Roles','roles',1,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values"
            + " ('permissions','roles','/permissions','Permissions','permissions',2,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update(
        "delete from summer_role_permission where domain_key='PLATFORM:platform' and permission_id"
            + " in (select permission_id from summer_permission_resource where"
            + " domain_key='PLATFORM:platform' and resource_type='MENU' and resource_id='roles')");
    var response =
        mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var body =
        json.readTree(response.getResponse().getContentAsString()).path("data").path("items");
    var parent =
        java.util.stream.StreamSupport.stream(body.spliterator(), false)
            .filter(x -> x.path("menuId").asText().equals("roles"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        parent.path("navOnly").asBoolean(), "Parent is visible only as a navigation directory");
    assertFalse(parent.path("pageEligible").asBoolean());
    assertEquals(0, parent.path("actions").size());
  }

  @Test
  void disabledEnterpriseAncestorBlocksOtherwiseQualifiedChild() throws Exception {
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values"
            + " ('block-parent-05',null,'/block-parent-05','Parent','parent',10,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values"
            + " ('block-child-05','block-parent-05','/block-child-05','Child','child',11,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update(
        "insert into summer_permission_resource select"
            + " domain_key,permission_id,'MENU','block-child-05' from summer_permission_resource"
            + " where domain_key='ENTERPRISE:ent-acme' and resource_id='permissions' and"
            + " resource_type='MENU'");
    db.update(
        "insert into"
            + " summer_menu_manifest_override(override_id,enterprise_id,menu_id,enabled,version,created_at,updated_at)"
            + " values"
            + " ('ent-acme:block-parent-05','ent-acme','block-parent-05',false,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    var denied =
        mvc.perform(
                get("/api/v1/enterprises/ent-acme/authorization/navigation")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var items = json.readTree(denied.getResponse().getContentAsString()).path("data").path("items");
    assertFalse(
        java.util.stream.StreamSupport.stream(items.spliterator(), false)
            .anyMatch(x -> x.path("menuId").asText().startsWith("block-")));
    db.update(
        "update summer_menu_manifest_override set enabled=true where"
            + " override_id='ent-acme:block-parent-05'");
    var allowed =
        mvc.perform(
                get("/api/v1/enterprises/ent-acme/authorization/navigation")
                    .with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    items = json.readTree(allowed.getResponse().getContentAsString()).path("data").path("items");
    assertTrue(
        java.util.stream.StreamSupport.stream(items.spliterator(), false)
            .anyMatch(
                x ->
                    x.path("menuId").asText().equals("block-child-05")
                        && x.path("pageEligible").asBoolean()));
  }

  @Test
  void missingAndUnknownModeNeverBecomeLegacy() throws Exception {
    db.update("delete from summer_authorization_domain where domain_key='PLATFORM:platform'");
    try {
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
          .andExpect(status().isForbidden());
    } finally {
      db.update(
          "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
              + " ('PLATFORM:platform','MANAGED',0)");
    }
    db.update(
        "update summer_authorization_domain set engine_mode='UNKNOWN' where"
            + " domain_key='PLATFORM:platform'");
    try {
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
          .andExpect(status().isForbidden());
    } finally {
      db.update(
          "update summer_authorization_domain set engine_mode='MANAGED' where"
              + " domain_key='PLATFORM:platform'");
    }
  }

  @Test
  void publishedEmptyCatalogDoesNotReviveBootstrapMenus() throws Exception {
    db.update(
        "update summer_menu_publication set published=true,manifest_version=999 where"
            + " publication_id='global'");
    var response =
        mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var body = json.readTree(response.getResponse().getContentAsString()).path("data");
    assertEquals(999, body.path("manifestVersion").asInt());
    assertEquals(0, body.path("items").size());
  }

  @Test
  void publicationStorageFailureIsDependencyFailure() throws Exception {
    db.execute("alter table summer_menu_publication rename to summer_menu_publication_fault05");
    try {
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
          .andExpect(status().isServiceUnavailable());
    } finally {
      db.execute("alter table summer_menu_publication_fault05 rename to summer_menu_publication");
    }
  }

  @Test
  void legacyMarkerNeedsActiveIdentityButNoManagedDelegation() throws Exception {
    db.update(
        "update summer_authorization_domain set engine_mode='LEGACY' where"
            + " domain_key='PLATFORM:platform'");
    var delegated =
        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .jwt()
            .jwt(x -> x.subject("summer-user").claim("scope", java.util.List.of("profile")));
    try {
      var response =
          mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
              .andExpect(status().isOk())
              .andReturn();
      assertEquals(
          "LEGACY",
          json.readTree(response.getResponse().getContentAsString())
              .path("data")
              .path("engineMode")
              .asText());
      db.update("update summer_auth_user set active=false where username='summer-user'");
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
          .andExpect(status().isForbidden());
    } finally {
      db.update("update summer_auth_user set active=true where username='summer-user'");
      db.update(
          "update summer_authorization_domain set engine_mode='MANAGED' where"
              + " domain_key='PLATFORM:platform'");
    }
    // Navigation is an active identity projection in both modes, independent of management scopes.
    mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.engineMode").value("MANAGED"));
    mvc.perform(get("/api/v1/platform/authorization/menu-manifest").with(delegated))
        .andExpect(status().isForbidden());
  }

  @Test
  void emptyPublicationPersistsVersionAndReceiptAndRejectsStaleEditor() throws Exception {
    String endpoint = "/api/v1/platform/authorization/menu-manifest";
    String body = "{\"manifestVersion\":9,\"items\":[]}";
    String key = java.util.UUID.randomUUID().toString();
    var call =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
            .with(user("summer-user"))
            .with(
                org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.csrf())
            .contentType("application/json")
            .header("If-Match", "\"8\"")
            .header("Idempotency-Key", key)
            .content(body);
    mvc.perform(call)
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("ETag", "\"9\""));
    mvc.perform(call).andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .with(user("summer-user"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json")
                .header("If-Match", "\"8\"")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .content(body))
        .andExpect(status().isPreconditionFailed());
    assertEquals(
        9,
        db.queryForObject(
            "select manifest_version from summer_menu_publication where publication_id='global'",
            Integer.class));
    assertTrue(
        db.queryForObject(
            "select published from summer_menu_publication where publication_id='global'",
            Boolean.class));
    var response =
        mvc.perform(get("/api/v1/platform/authorization/navigation").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        0,
        json.readTree(response.getResponse().getContentAsString())
            .path("data")
            .path("items")
            .size());
  }

  @Test
  void fullLengthMenuIdentityCanBeBoundAndQualifiedWithoutTruncation() throws Exception {
    String menuId = "menu-length-05-" + "x".repeat(145);
    assertEquals(160, menuId.length());
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values (?,?,'Long identity','',99,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        menuId,
        "/" + menuId);
    String groupId =
        db.queryForObject(
            "select group_id from summer_permission_group where domain_key='PLATFORM:platform'"
                + " limit 1",
            String.class);
    var created =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/platform/permissions")
                    .with(user("summer-user"))
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .content(
                        json.writeValueAsString(
                            java.util.Map.of(
                                "code",
                                "MENU_LENGTH_05",
                                "name",
                                "Long menu identity",
                                "type",
                                "MENU",
                                "groupId",
                                groupId,
                                "accessMode",
                                "AUTHORIZED",
                                "status",
                                "ACTIVE"))))
            .andExpect(status().isCreated())
            .andReturn();
    String permissionId =
        json.readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("permissionId")
            .asText();
    db.update(
        "insert into summer_role_permission(domain_key,role_id,permission_id) select"
            + " domain_key,role_id,? from summer_permission_user_role where"
            + " domain_key='PLATFORM:platform' and username='summer-user' limit 1",
        permissionId);
    String path = "/api/v1/platform/permissions/" + permissionId + "/menus";
    var before =
        json.readTree(
                mvc.perform(get(path).with(user("summer-user")))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data");
    java.util.function.BiFunction<
            String,
            Long,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>
        put =
            (body, version) ->
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                    .with(user("summer-user"))
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType("application/json")
                    .header("If-Match", "\"" + version + "\"")
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .content(body);
    var bound =
        json.readTree(
                mvc.perform(
                        put.apply(
                            json.writeValueAsString(
                                java.util.Map.of("menuIds", java.util.List.of(menuId))),
                            before.path("version").asLong()))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("data");
    try {
      assertEquals(menuId, bound.path("resourceIds").get(0).asText());
      assertEquals(
          160,
          db.queryForObject(
              "select char_length(resource_id) from summer_permission_resource where"
                  + " domain_key='PLATFORM:platform' and permission_id=? and resource_type='MENU'",
              Integer.class,
              permissionId));
      assertEquals(
          1,
          db.queryForObject(
              "select count(*) from summer_permission_resource_control where"
                  + " domain_key='PLATFORM:platform' and resource_type='MENU' and"
                  + " control_kind='RESOURCE' and target_id=?",
              Integer.class,
              menuId));
      var navigation =
          json.readTree(
                  mvc.perform(
                          get("/api/v1/platform/authorization/navigation")
                              .with(user("summer-user")))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .path("data")
              .path("items");
      assertTrue(
          java.util.stream.StreamSupport.stream(navigation.spliterator(), false)
              .anyMatch(
                  x ->
                      menuId.equals(x.path("menuId").asText())
                          && x.path("pageEligible").asBoolean()));
      var revision =
          db.queryForObject(
              "select revision from summer_authorization_domain where"
                  + " domain_key='PLATFORM:platform'",
              Long.class);
      var receipts =
          db.queryForObject("select count(*) from summer_permission_mutation", Integer.class);
      mvc.perform(
              put.apply(
                  json.writeValueAsString(
                      java.util.Map.of("menuIds", java.util.List.of(menuId + "y"))),
                  bound.path("version").asLong()))
          .andExpect(status().isBadRequest());
      assertEquals(
          revision,
          db.queryForObject(
              "select revision from summer_authorization_domain where"
                  + " domain_key='PLATFORM:platform'",
              Long.class));
      assertEquals(
          receipts,
          db.queryForObject("select count(*) from summer_permission_mutation", Integer.class));
      var unchanged =
          json.readTree(
                  mvc.perform(get(path).with(user("summer-user")))
                      .andExpect(status().isOk())
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .path("data");
      assertEquals(bound, unchanged);
    } finally {
      mvc.perform(
              put.apply(
                  json.writeValueAsString(java.util.Map.of("menuIds", java.util.List.of())),
                  bound.path("version").asLong()))
          .andExpect(status().isOk());
      db.update("delete from summer_menu_manifest_item where menu_id=?", menuId);
    }
  }

  @Test
  void associationInputRejectsUnknownFieldsWithoutAnyMutation() throws Exception {
    db.update(
        "insert into"
            + " summer_menu_manifest_item(menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)"
            + " values"
            + " ('strict-menu-05','/strict-menu-05','Strict','strict',99,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    String id =
        db.queryForObject(
            "select permission_id from summer_permission where domain_key='PLATFORM:platform' and"
                + " type='MENU' order by permission_id limit 1",
            String.class);
    String path = "/api/v1/platform/permissions/" + id + "/menus";
    var read =
        mvc.perform(get(path).with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    long version =
        json.readTree(read.getResponse().getContentAsString())
            .path("data")
            .path("version")
            .asLong();
    long revision =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key='PLATFORM:platform'",
            Long.class);
    int receipts =
        db.queryForObject("select count(*) from summer_permission_mutation", Integer.class);
    var links =
        db.queryForList(
            "select * from summer_permission_resource where domain_key='PLATFORM:platform' and"
                + " permission_id=?",
            id);
    for (String extra : java.util.List.of("accessMode", "permissionCode", "menuIdz")) {
      var body = new java.util.LinkedHashMap<String, Object>();
      body.put("menuIds", java.util.List.of("strict-menu-05"));
      body.put(extra, "PUBLIC");
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                  .with(user("summer-user"))
                  .with(
                      org.springframework.security.test.web.servlet.request
                          .SecurityMockMvcRequestPostProcessors.csrf())
                  .contentType("application/json")
                  .header("If-Match", "\"" + version + "\"")
                  .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                  .content(json.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    assertEquals(
        revision,
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key='PLATFORM:platform'",
            Long.class));
    assertEquals(
        receipts,
        db.queryForObject("select count(*) from summer_permission_mutation", Integer.class));
    assertEquals(
        links,
        db.queryForList(
            "select * from summer_permission_resource where domain_key='PLATFORM:platform' and"
                + " permission_id=?",
            id));
  }
}
