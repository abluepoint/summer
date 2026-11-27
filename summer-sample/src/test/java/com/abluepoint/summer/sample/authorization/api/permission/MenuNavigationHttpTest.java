package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import java.util.Map;

/** HTTP navigation contract; isolated SQL fixtures establish resource hierarchy and role facts. */
@org.springframework.test.context.jdbc.Sql("/fixtures/menu-client-dictionary.sql")
@SpringBootTest(
    properties = {
      "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme",
      "menu.contract.test=true"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
  void clientNavigationUsesPublishedLayoutAndChecksAncestorsBeforeFiltering() throws Exception {
    db.update("""
        insert into summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,client_code,created_at,updated_at)
        values ('roles',null,'/roles','后台岗位','',9,8,'mobile',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               ('permissions','roles','/permissions','业务权限','',7,8,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """);
    String path = "/api/v1/platform/authorization/navigation";
    mvc.perform(get(path).param("clientCode","web").with(user("summer-user"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].titleKey").value("业务权限"))
        .andExpect(jsonPath("$.data.items[0].parentMenuId").isEmpty());
    mvc.perform(get(path).param("clientCode","mobile").with(user("summer-user"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].menuId").value("roles"));
    db.update("insert into summer_menu_domain_layout(layout_id,domain_key,menu_id,enabled,version) values ('disabled-mobile','PLATFORM:platform','roles',false,2)");
    mvc.perform(get(path).param("clientCode","web").with(user("summer-user"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void publishedPlatformRolesHaveIndependentLayoutAndEligibility() throws Exception {
    db.update("""
        insert into summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,client_code,created_at,updated_at)
        values ('roles',null,'/roles','角色与权限','',0,8,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               ('access-control',null,'/access-control','访问控制','',1,8,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
               ('platform/roles','access-control','/platform/roles','平台角色与权限','',4,8,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        """);
    String platform = "/api/v1/platform/authorization/navigation";
    var actor = user("summer-user");
    mvc.perform(get(platform).param("clientCode","web").with(actor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    db.update("""
        insert into summer_permission_resource
        select domain_key,permission_id,'MENU','platform/roles' from summer_permission_resource
        where resource_type='MENU' and resource_id='roles'
        """);
    mvc.perform(get(platform).param("clientCode","web").with(actor)).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].navOnly").value(true))
        .andExpect(jsonPath("$.data.items[1].parentMenuId").value("access-control"));
    mvc.perform(get("/api/v1/enterprises/ent-acme/authorization/navigation").param("clientCode","web").with(actor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].menuId").value("roles"));
    db.update("update summer_menu_manifest_item set client_code='tablet' where menu_id='platform/roles'");
    mvc.perform(get(platform).param("clientCode","web").with(actor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  void dictionaryManagementPagesRequirePlatformQualification() throws Exception {
    for (String id : java.util.List.of("dictionary/manage", "dictionary/groups")) {
      db.update("""
          insert into summer_menu_manifest_item(menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at)
          values (?,?,?,'',0,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
          """, id, "/" + id, id);
      db.update("""
          insert into summer_permission_resource
          select domain_key,permission_id,'MENU',? from summer_permission_resource
          where resource_type='MENU' and resource_id='roles'
          """, id);
    }
    var actor = user("summer-user");
    mvc.perform(get("/api/v1/enterprises/ent-acme/authorization/navigation").with(actor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    mvc.perform(get("/api/v1/platform/authorization/navigation").with(actor))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].menuId",
            org.hamcrest.Matchers.containsInAnyOrder("dictionary/manage", "dictionary/groups")));
  }

  @Test
  void disabledPlatformAncestorBlocksOtherwiseQualifiedChild() throws Exception {
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
            + " summer_menu_domain_layout(layout_id,domain_key,menu_id,enabled,version)"
            + " values"
            + " ('platform:block-parent-05','PLATFORM:platform','block-parent-05',false,2)");
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
        "update summer_menu_domain_layout set enabled=true where"
            + " layout_id='platform:block-parent-05'");
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
  void retiredLegacyMarkerCannotExposeNavigationAndManagedIdentityRemainsRequired() throws Exception {
    var delegated = jwt().jwt(x -> x.subject("summer-user").claim("scope", java.util.List.of("profile")));
    db.update("update summer_authorization_domain set engine_mode='LEGACY' where domain_key='PLATFORM:platform'");
    try {
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
          .andExpect(status().isForbidden());
      org.junit.jupiter.api.Assertions.assertEquals("LEGACY", db.queryForObject(
          "select engine_mode from summer_authorization_domain where domain_key='PLATFORM:platform'", String.class));
    } finally {
      db.update("update summer_authorization_domain set engine_mode='MANAGED' where domain_key='PLATFORM:platform'");
    }
    mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.engineMode").value("MANAGED"));
    db.update("update summer_auth_user set active=false where username='summer-user'");
    try {
      mvc.perform(get("/api/v1/platform/authorization/navigation").with(delegated))
          .andExpect(status().isForbidden());
    } finally { db.update("update summer_auth_user set active=true where username='summer-user'"); }
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

  @Test
  @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
  void eitherMenuPermissionAloneQualifiesTheSamePageAndNeitherDoesNot() throws Exception {
    String subject = "menu-or-" + java.util.UUID.randomUUID();
    String parent = "menu-or-parent-" + java.util.UUID.randomUUID();
    String page = "menu-or-page-" + java.util.UUID.randomUUID();
    causalIdentity(subject);
    db.update("insert into summer_platform_administrator(username,display_name) values(?,?)",subject,subject);
    causalPublish(parent, java.util.List.of(page));
    causalAssociations(page, java.util.Set.of());
    String p1 = causalPermission(java.util.List.of(page));
    String p2 = causalPermission(java.util.List.of(page));
    String r1 = causalRole(p1), r2 = causalRole(p2);
    var permissions = java.util.Set.of(p1, p2);
    causalAssociations(page, permissions);
    causalPage(causalNavigation(subject, java.util.List.of()), page, false);

    causalAssign(subject, java.util.List.of(r1));
    var firstOnly = causalNavigation(subject, java.util.List.of(r1));
    causalPage(firstOnly, page, true);
    causalDirectory(firstOnly, parent);
    causalAssociations(page, permissions);

    causalAssign(subject, java.util.List.of(r2));
    var secondOnly = causalNavigation(subject, java.util.List.of(r2));
    causalPage(secondOnly, page, true);
    causalDirectory(secondOnly, parent);
    causalAssociations(page, permissions);

    causalAssign(subject, java.util.List.of());
    causalPage(causalNavigation(subject, java.util.List.of()), page, false);
    // Both active, same-mode permissions stay associated; only the user's roles changed.
    causalAssociations(page, permissions);
    assertEquals(java.util.List.of(p1), db.queryForList(
        "select permission_id from summer_role_permission where domain_key='PLATFORM:platform' and role_id=?", String.class, r1));
    assertEquals(java.util.List.of(p2), db.queryForList(
        "select permission_id from summer_role_permission where domain_key='PLATFORM:platform' and role_id=?", String.class, r2));
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

  }

  private String causalPermission(java.util.List<String> menus) throws Exception {
    String group = causalCreate("/api/v1/platform" + "/permission-groups", Map.of("name", "Causal menus")).path("groupId").asText();
    String permission = causalCreate("/api/v1/platform" + "/permissions", Map.of(
        "name", "Causal MENU", "code", java.util.UUID.randomUUID().toString(), "groupId", group,
        "type", "MENU", "accessMode", "AUTHORIZED", "status", "ACTIVE")).path("permissionId").asText();
    var bound = causalReplace("/api/v1/platform" + "/permissions/" + permission + "/menus", Map.of("menuIds", menus));
    var actual = new java.util.HashSet<String>();
    for (var id : bound.path("resourceIds")) actual.add(id.asText());
    assertEquals(new java.util.HashSet<>(menus), actual);
    assertEquals(new java.util.HashSet<>(menus), new java.util.HashSet<>(db.queryForList(
        "select resource_id from summer_permission_resource where domain_key=? and permission_id=? and resource_type='MENU'",
        String.class, "PLATFORM:platform", permission)));
    return permission;
  }

  private String causalRole(String permission) throws Exception {
    String role = causalCreate("/api/v1/platform" + "/roles", Map.of("name", "Causal role",
        "code", java.util.UUID.randomUUID().toString(), "permissionIds", java.util.List.of(permission))).path("roleId").asText();
    assertEquals(java.util.List.of(permission), db.queryForList(
        "select permission_id from summer_role_permission where domain_key=? and role_id=?",
        String.class, "PLATFORM:platform", role));
    return role;
  }

  private void causalAssign(String subject, java.util.List<String> roles) throws Exception {
    var result = causalReplace("/api/v1/platform" + "/users/" + subject + "/roles", Map.of("roleIds", roles));
    var actual = new java.util.HashSet<String>();
    for (var role : result.path("roleIds")) actual.add(role.asText());
    assertEquals(new java.util.HashSet<>(roles), actual);
  }

  private tools.jackson.databind.JsonNode causalNavigation(String subject, java.util.List<String> roles) throws Exception {
    assertEquals(1, db.queryForObject("select count(*) from summer_auth_user where username=? and active=true", Integer.class, subject));

    assertEquals(new java.util.HashSet<>(roles), new java.util.HashSet<>(db.queryForList(
        "select role_id from summer_permission_user_role where domain_key=? and username=?", String.class, "PLATFORM:platform", subject)));
    var result = mvc.perform(get("/api/v1/platform" + "/authorization/navigation").with(user(subject)))
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
        String.class, "PLATFORM:platform", menu)));
    assertEquals(permissions.size(), db.queryForObject(
        "select count(*) from summer_permission_resource x join summer_permission p on p.domain_key=x.domain_key and p.permission_id=x.permission_id where x.domain_key=? and x.resource_type='MENU' and x.resource_id=? and p.type='MENU' and p.access_mode='AUTHORIZED' and p.status='ACTIVE'",
        Integer.class, "PLATFORM:platform", menu));
  }
}
