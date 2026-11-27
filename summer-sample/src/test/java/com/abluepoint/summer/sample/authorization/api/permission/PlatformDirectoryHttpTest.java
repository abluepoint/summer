package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abluepoint.summer.sample.authorization.application.permission.*;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class PlatformDirectoryHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired ManagedInterfaceResources resources;
  @Autowired PermissionResourceAssociations associations;
  @Autowired MenuPublicationState publication;
  @Autowired PlatformTransactionManager transactions;
  static final String BASE = "/api/v1/platform";
  static final String DEPTS = BASE + "/platform-departments";
  static final String ADMIN = "summer-user";

  void mutate(MockHttpServletRequestBuilder req, String body, int expected) throws Exception {
    long revision =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key='PLATFORM:platform'",
            Long.class);
    mvc.perform(
            req.with(user(ADMIN))
                .with(csrf())
                .header("If-Match", "\"" + revision + "\"")
                .contentType("application/json")
                .content(body))
        .andExpect(status().is(expected));
  }

  String department(String name) throws Exception {
    mutate(post(DEPTS), "{\"name\":\"" + name + "\",\"sortOrder\":1,\"enabled\":true}", 200);
    return db.queryForObject(
        "select id from summer_platform_department where name=?", String.class, name);
  }

  @Test
  void rosterScopesAndDepartmentLifecycle() throws Exception {
    var sql =
        new ClassPathResource("db/migration/mysql/V196__platform_organization.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    for (String stmt :
        sql.substring(sql.indexOf("CREATE TABLE summer_platform_department"))
            .replace(" STORED", "")
            .split(";")) if (!stmt.isBlank()) db.execute(stmt);
    db.execute(
        "create table if not exists summer_permission_selected_department(domain_key"
            + " varchar(96),permission_id varchar(96),department_id varchar(64))");
    var installation = new PlatformDirectoryInstallation(db, resources, associations, publication);
    new TransactionTemplate(transactions).executeWithoutResult(tx -> installation.install());
    String role =
        db.queryForObject(
            "select role_id from summer_permission_user_role where domain_key='PLATFORM:platform'"
                + " and username=?",
            String.class,
            ADMIN);
    db.update(
        "insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at)"
            + " select 'new-person',password_hash,role,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP"
            + " from summer_auth_user where username=?",
        ADMIN);
    mvc.perform(
            put(BASE + "/users/new-person/roles")
                .with(user(ADMIN))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .header("Idempotency-Key", "before-enrollment")
                .contentType("application/json")
                .content("{\"roleIds\":[\"" + role + "\"]}"))
        .andExpect(status().isBadRequest());
    mutate(
        post(BASE + "/administrators"),
        "{\"username\":\"new-person\",\"displayName\":\"测试人员\"}",
        200);
    mvc.perform(get(BASE+"/authorization/navigation").with(user(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.items[?(@.menuId == 'platform/organization')].parentMenuId")
            .value(org.hamcrest.Matchers.hasItem("platform-management")));
    mvc.perform(put(BASE+"/users/new-person/roles").with(user(ADMIN)).with(csrf())
        .header("If-Match","\"0\"").header("Idempotency-Key","enrolled-role")
        .contentType("application/json").content("{\"roleIds\":[\""+role+"\"]}")).andExpect(status().isOk());
    mvc.perform(put(BASE+"/users/new-person/roles").with(user(ADMIN)).with(csrf())
        .header("If-Match","\"1\"").header("Idempotency-Key","remove-last-role")
        .contentType("application/json").content("{\"roleIds\":[]}")).andExpect(status().isOk());
    String first = department("First"), second = department("Second");
    String path = DEPTS + "/" + first + "/members/new-person";
    mutate(put(path), "{\"action\":\"JOIN\"}", 200);
    mutate(put(path), "{\"action\":\"LEADER\"}", 200);
    mutate(put(DEPTS + "/" + second + "/members/new-person"), "{\"action\":\"JOIN\"}", 200);
    var snapshot = mvc.perform(get(DEPTS).with(user(ADMIN))).andExpect(status().isOk()).andReturn();
    var data = json.readTree(snapshot.getResponse().getContentAsString()).path("data");
    assertEquals(2, data.path("memberships").size());
    assertTrue(data.path("all").asBoolean());
    mutate(delete(DEPTS + "/" + first), "", 409);
    db.update(
        "update summer_permission_data_scope set scope_preset='PLATFORM_SPECIFIED_SUBTREES' where"
            + " permission_id='platform-directory-scope'");
    db.update(
        "insert into summer_permission_selected_department"
            + " values('PLATFORM:platform','platform-directory-scope',?)",
        first);
    var limited = mvc.perform(get(DEPTS).with(user(ADMIN))).andExpect(status().isOk()).andReturn();
    assertEquals(
        1,
        json.readTree(limited.getResponse().getContentAsString())
            .path("data")
            .path("memberships")
            .size());
    mutate(put(path), "{\"action\":\"LEAVE\"}", 403);
    assertEquals(
        2, db.queryForObject("select count(*) from summer_platform_membership", Integer.class));
    db.update(
        "update summer_permission_data_scope set scope_preset='PLATFORM_ALL_DEPARTMENTS' where"
            + " permission_id='platform-directory-scope'");
    mutate(put(path), "{\"action\":\"LEAVE\"}", 200);
    assertEquals(
        second,
        db.queryForObject(
            "select department_id from summer_platform_membership where primary_department=true",
            String.class));
    mutate(delete(DEPTS + "/" + first), "", 200);
    mutate(delete(BASE + "/administrators/new-person"), "", 200);
    assertEquals(
        0, db.queryForObject("select count(*) from summer_platform_membership", Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "select count(*) from summer_auth_user where username='new-person'", Integer.class));
    mutate(delete(BASE + "/administrators/" + ADMIN), "", 200);
    mvc.perform(get(BASE + "/administrators").with(user(ADMIN))).andExpect(status().isForbidden());
    new TransactionTemplate(transactions).executeWithoutResult(tx -> installation.install());
    assertEquals(
        0, db.queryForObject("select count(*) from summer_platform_administrator", Integer.class));
  }
}
