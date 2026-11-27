package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceAssociations;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Fast real MVC/JDBC contract checks; TCP MySQL/Redis/Chrome evidence is kept separately. */
@SpringBootTest(
    properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RolePermissionHttpTest {
  @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap bootstrap;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired PermissionResourceAssociations associations;
  @Autowired PlatformTransactionManager transactions;
  @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
  static final String BASE = "/api/v1/platform";

  @TestConfiguration
  @org.springframework.context.annotation.Import(TrustedManagedTestFixture.class)
  static class Schema {
    @Bean
    ApplicationRunner permissionSchema(JdbcTemplate db) {
      return args -> {
        for (String name :
            List.of(
                "V12__authorization_roles_and_bindings.sql",
                "V60__permission_management.sql",
                "V70__role_permission_and_resource_associations.sql",
                "V80__permission_resource_controls.sql",
                "V81__managed_interface_lifecycle.sql",
                "V92__support_full_length_menu_resource_ids.sql")) {
          String sql =
              new String(
                  new ClassPathResource("db/migration/mysql/" + name).getContentAsByteArray(),
                  StandardCharsets.UTF_8);
          // Hibernate creates the role and capability entities; V12 owns their legacy join table.
          if (name.startsWith("V12")) {
            int joinTable = sql.indexOf("CREATE TABLE summer_authorization_role_capability (");
            sql = sql.substring(joinTable, sql.indexOf(';', joinTable) + 1);
          }
          if (name.startsWith("V60")) sql = sql.substring(0, sql.indexOf("ALTER TABLE"));
          for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";"))
            if (!statement.isBlank())
              db.execute(
                  statement.replace(
                      "CREATE TABLE summer_permission_role",
                      "CREATE TABLE IF NOT EXISTS summer_permission_role"));
        }
        db.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id");
      };
    }
  }

  @Test
  void platformRoleDeletionChecksVersionBindingsAndReplaysSuccess() throws Exception {
    var actor = user("summer-user");
    mvc.perform(get(BASE + "/authorization/operations").with(actor).param("actions", "roles.delete"))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .jsonPath("$.data.actions['roles.delete']").value(true));
    var builtin = db.queryForMap("select r.role_id, r.version from summer_authorization_role r"
        + " join summer_permission_role p on p.role_id=r.role_id where p.domain_key='PLATFORM:platform'"
        + " and r.built_in=true and r.code='PLATFORM:permission-administrator'");
    mvc.perform(delete(BASE + "/roles/" + builtin.get("role_id")).with(actor).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", "\"" + builtin.get("version") + "\""))
        .andExpect(status().isConflict());
    String permission = db.queryForObject(
        "select permission_id from summer_permission where domain_key='PLATFORM:platform' and code='management-read'",
        String.class);
    var created = mvc.perform(post(BASE + "/roles").with(actor).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name", "Temporary", "code", "temp-" + UUID.randomUUID(),
                "permissionIds", List.of(permission)))))
        .andExpect(status().isCreated()).andReturn();
    String id = json.readTree(created.getResponse().getContentAsString()).path("data").path("roleId").asText();
    String path = BASE + "/roles/" + id;
    var remove = delete(path).with(actor).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
        .header("If-Match", "\"1\"");
    mvc.perform(delete(path).with(user("unassigned")).with(csrf())).andExpect(status().isForbidden());
    mvc.perform(delete(path).with(actor).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
        .header("If-Match", "\"0\"")).andExpect(status().isPreconditionFailed());
    db.update("insert into summer_permission_user_role values ('PLATFORM:platform','summer-user',?)", id);
    // A historical built-in flag alone does not make this the default administrator.
    db.update("update summer_authorization_role set built_in=true where role_id=?", id);
    Long bindingVersion = db.queryForObject("select version from summer_permission_user where"
        + " domain_key='PLATFORM:platform' and username='summer-user'", Long.class);
    mvc.perform(get(path).with(actor)).andExpect(status().isOk()).andExpect(
        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.defaultPlatformAdministrator").value(false));
    mvc.perform(remove).andExpect(status().isOk());
    mvc.perform(remove).andExpect(status().isOk());
    assertEquals(bindingVersion + 1, db.queryForObject("select version from summer_permission_user where"
        + " domain_key='PLATFORM:platform' and username='summer-user'", Long.class));
    assertEquals(0, db.queryForObject("select count(*) from summer_permission_user_role where role_id=?", Integer.class, id));
    assertEquals(1, db.queryForObject("select count(*) from summer_permission_user_role where"
        + " domain_key='PLATFORM:platform' and username='summer-user' and role_id=?", Integer.class, builtin.get("role_id")));
    mvc.perform(put(BASE + "/users/summer-user/roles").with(actor).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", "\"" + bindingVersion + "\"")
        .contentType("application/json")
        .content(json.writeValueAsString(Map.of("roleIds", List.of(builtin.get("role_id"), id)))))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(get(path).with(actor)).andExpect(status().isNotFound());
  }

  @Test
  void unassignedSessionCannotReadPlatformRoles() throws Exception {
    mvc.perform(get(BASE + "/roles").with(user("unassigned"))).andExpect(status().isForbidden());
  }

  @Test
  void rawDelegatedScopeCannotBeReplacedByLegacyAuthorities() throws Exception {
    mvc.perform(
            get(BASE + "/authorization/status")
                .with(
                    jwt()
                        .jwt(j -> j.subject("summer-user").claim("scope", List.of("customer.read")))
                        .authorities(
                            new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void sessionManagementRejectsLegacyFieldsAndReturnsVersion() throws Exception {
    mvc.perform(
            post(BASE + "/roles")
                .with(user("summer-user"))
                .with(csrf())
                .contentType("application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("{\"name\":\"Legacy\",\"code\":\"legacy\",\"capabilityIds\":[]}"))
        .andExpect(status().isBadRequest());
    var created =
        mvc.perform(
                post(BASE + "/roles")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content("{\"name\":\"Explicit\",\"code\":\"explicit\",\"permissionIds\":[]}"))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        json.readTree(created.getResponse().getContentAsString())
            .path("data")
            .path("roleId")
            .asText();
    assertEquals(
        "\"1\"",
        mvc.perform(get(BASE + "/roles/" + id).with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("ETag"));
  }

  @Test
  void associationChecksIncludeDisabledPermissionsAndRollbackModeConflict() throws Exception {
    var p =
        db.queryForList(
                "select * from summer_permission where domain_key='PLATFORM:platform' and"
                    + " code='PUBLIC'")
            .getFirst();
    String group = p.get("group_id").toString();
    var response =
        mvc.perform(
                post(BASE + "/permissions")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "name",
                                "Disabled public",
                                "code",
                                "disabled-public",
                                "type",
                                "INTERFACE",
                                "accessMode",
                                "PUBLIC",
                                "status",
                                "DISABLED",
                                "groupId",
                                group))))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        json.readTree(response.getResponse().getContentAsString())
            .path("data")
            .path("permissionId")
            .asText();
    String resource =
        db.queryForObject(
            "select resource_id from summer_permission_resource where"
                + " domain_key='PLATFORM:platform' and permission_id=?",
            String.class,
            p.get("permission_id"));
    var tx = new TransactionTemplate(transactions);
    tx.executeWithoutResult(
        s ->
            associations.bind(
                ResourceAuthorization.Domain.platform(),
                id,
                new ResourceAuthorization.Resource(resource, "INTERFACE", "MVC")));
    long before =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key='PLATFORM:platform'",
            Long.class);
    mvc.perform(
            patch(BASE + "/permissions/" + id)
                .with(user("summer-user"))
                .with(csrf())
                .contentType("application/json")
                .header("If-Match", "\"1\"")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("{\"accessMode\":\"AUTHENTICATED\"}"))
        .andExpect(status().isConflict());
    assertEquals(
        before,
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key='PLATFORM:platform'",
            Long.class));
    assertEquals(
        "PUBLIC",
        db.queryForObject(
            "select access_mode from summer_permission where domain_key='PLATFORM:platform' and"
                + " permission_id=?",
            String.class,
            id));
    assertThrows(
        SummerException.class,
        () ->
            tx.executeWithoutResult(
                s ->
                    associations.bind(
                        ResourceAuthorization.Domain.platform(),
                        id,
                        new ResourceAuthorization.Resource("wrong-type", "MENU", "test"))));
  }

  @Test
  void membershipStatusSharesTheAuthorizationRevisionTransaction() {
    db.update(
        "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
            + " ('ENTERPRISE:ent-acme','MANAGED',0)");
    var alice = members.forSubject("ent-acme", "alice").orElseThrow();
    members.changeStatus("ent-acme", alice.memberId(), "DISABLED");
    assertEquals(
        1L,
        db.queryForObject(
            "select revision from summer_authorization_domain where"
                + " domain_key='ENTERPRISE:ent-acme'",
            Long.class));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from summer_authorization_change_outbox where"
                + " enterprise_id='ENTERPRISE:ent-acme' and event_type='MEMBERSHIP_CHANGED'",
            Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from summer_authorization_audit where"
                + " enterprise_id='ENTERPRISE:ent-acme' and action='MEMBERSHIP_CHANGED'",
            Integer.class));
  }

  @Test
  void interfacePermissionDoesNotGrantManagementMenuNavigation() throws Exception {
    String subject="interface-only-navigation";
    db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) select ?,password_hash,role,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP from summer_auth_user where username='summer-user'",subject);
    db.update("insert into summer_platform_administrator(username,display_name) values(?,?)",subject,subject);
    String readPermission=db.queryForObject("select permission_id from summer_permission where domain_key='PLATFORM:platform' and code='management-read'",String.class);
    var created=mvc.perform(post(BASE+"/roles").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key","interface-only-navigation-role").contentType("application/json")
        .content(json.writeValueAsString(Map.of("name","Interface only","code","interface-only-navigation","permissionIds",List.of(readPermission)))))
        .andExpect(status().isCreated()).andReturn();
    String roleId=json.readTree(created.getResponse().getContentAsString()).path("data").path("roleId").asText();
    mvc.perform(put(BASE+"/users/"+subject+"/roles").with(user("summer-user")).with(csrf())
        .header("If-Match","\"0\"").header("Idempotency-Key","interface-only-navigation-user").contentType("application/json")
        .content(json.writeValueAsString(Map.of("roleIds",List.of(roleId)))))
        .andExpect(status().isOk());
    mvc.perform(get(BASE+"/roles").with(user(subject))).andExpect(status().isOk());
    var result =
        mvc.perform(get(BASE + "/authorization/navigation").with(user(subject)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(
        0,
        json.readTree(result.getResponse().getContentAsString()).path("data").path("items").size());
    result =
        mvc.perform(get(BASE + "/authorization/navigation").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    Set<String> administratorMenus = new HashSet<>();
    json.readTree(result.getResponse().getContentAsString()).path("data").path("items")
        .forEach(item -> administratorMenus.add(item.path("menuId").asText()));
    assertEquals(Set.of("roles", "permissions", "menu-manifest", "interface-resources", "oauth-clients",
        "auth-users", "organization"), administratorMenus);
  }

  @Test
  void roleCollectionIsBoundedStableAndRejectsMalformedPaging() throws Exception {
    for (String code : List.of("paging-a", "paging-b"))
      mvc.perform(
              post(BASE + "/roles")
                  .with(user("summer-user"))
                  .with(csrf())
                  .contentType("application/json")
                  .header("Idempotency-Key", code)
                  .content(
                      json.writeValueAsString(
                          Map.of("name", code, "code", code, "permissionIds", List.of()))))
          .andExpect(status().isCreated());
    var one =
        mvc.perform(get(BASE + "/roles?page=1&size=1").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    var two =
        mvc.perform(get(BASE + "/roles?page=2&size=1").with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn();
    String first =
        json.readTree(one.getResponse().getContentAsString())
            .path("data")
            .path("items")
            .get(0)
            .path("roleId")
            .asText();
    String second =
        json.readTree(two.getResponse().getContentAsString())
            .path("data")
            .path("items")
            .get(0)
            .path("roleId")
            .asText();
    assertTrue(first.compareTo(second) < 0);
    for (String query :
        List.of("page=0", "size=201", "size=abc", "page=2147483648", "page=1&page=2"))
      mvc.perform(get(BASE + "/roles?" + query).with(user("summer-user")))
          .andExpect(status().isBadRequest());
  }

  @Test
  void sameNamedEnterpriseCannotReadWriteOrBindPlatformManagedRoles() throws Exception {
    String actor = "summer-user";
    db.update("insert into summer_enterprise(enterprise_id,name,code,status,version,created_at,updated_at) values ('platform','Mixed','mixed','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values ('platform','mixed-member',?,'Mixed','mixed@example.test','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", actor);
    var seed = com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap.class
        .getDeclaredMethod("seedNewDomain", ResourceAuthorization.Domain.class);
    seed.setAccessible(true);
    seed.invoke(bootstrap, ResourceAuthorization.Domain.enterprise("platform"));
    String url = "/api/v1/enterprises/platform";
    var created = mvc.perform(post(url + "/roles").with(user(actor)).with(csrf())
        .header("Idempotency-Key", "mixed-own-create").contentType("application/json")
        .content("{\"name\":\"Own managed role\",\"code\":\"own-managed\",\"permissionIds\":[]}"))
        .andExpect(status().isCreated()).andReturn().getResponse();
    String own = json.readTree(created.getContentAsString()).path("data").path("roleId").asText();
    String foreign = db.queryForObject("select role_id from summer_permission_role where domain_key='PLATFORM:platform' order by role_id limit 1", String.class);
    var before = db.queryForMap("select name,code,status,version from summer_authorization_role where role_id=?", foreign);
    long revision = db.queryForObject("select revision from summer_authorization_domain where domain_key='PLATFORM:platform'", Long.class);
    int mutations = db.queryForObject("select count(*) from summer_permission_mutation where domain_key='PLATFORM:platform'", Integer.class);
    var auth = jwt().jwt(j -> j.subject(actor).claim("scope", List.of("enterprise.roles.read", "enterprise.roles.write"))
        .claim("enterprise_capabilities", Map.of("platform", List.of("enterprise.roles.read", "enterprise.roles.write"))))
        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.roles.read"), new SimpleGrantedAuthority("SCOPE_enterprise.roles.write"));
    mvc.perform(get(url + "/roles/" + foreign).with(auth)).andExpect(status().isNotFound());
    mvc.perform(get(url + "/roles/" + foreign + "/permissions").with(auth)).andExpect(status().isNotFound());
    mvc.perform(get(url + "/roles/" + foreign + "/capabilities").with(auth)).andExpect(status().isBadRequest());
    mvc.perform(patch(url + "/roles/" + foreign).with(auth).with(csrf())
        .header("If-Match", "\"" + before.get("version") + "\"")
        .header("Idempotency-Key", "mixed-patch").contentType("application/json").content("{\"status\":\"DISABLED\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(put(url + "/users/" + actor + "/roles").with(auth).with(csrf())
        .header("If-Match", "\"1\"").header("Idempotency-Key", "mixed-binding")
        .contentType("application/json").content(json.writeValueAsString(Map.of("roleIds", List.of(foreign)))))
        .andExpect(status().isNotFound());
    assertEquals(before, db.queryForMap("select name,code,status,version from summer_authorization_role where role_id=?", foreign));
    assertEquals(revision, db.queryForObject("select revision from summer_authorization_domain where domain_key='PLATFORM:platform'", Long.class));
    assertEquals(mutations, db.queryForObject("select count(*) from summer_permission_mutation where domain_key='PLATFORM:platform'", Integer.class));
    mvc.perform(get(url + "/roles/" + own).with(auth)).andExpect(status().isOk());
    mvc.perform(patch(url + "/roles/" + own).with(auth).with(csrf())
        .header("If-Match", created.getHeader("ETag")).header("Idempotency-Key", "mixed-own-patch")
        .contentType("application/json").content("{\"name\":\"Still isolated\"}"))
        .andExpect(status().isOk());
    assertEquals(revision, db.queryForObject("select revision from summer_authorization_domain where domain_key='PLATFORM:platform'", Long.class));
  }
}
