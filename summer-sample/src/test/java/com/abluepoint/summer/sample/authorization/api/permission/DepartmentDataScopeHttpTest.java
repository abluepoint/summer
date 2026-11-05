package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DepartmentDataScopeHttpTest {
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;

  @org.springframework.boot.test.context.TestConfiguration
  static class DataSchema {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.ApplicationRunner departmentDataSchema(
        org.springframework.jdbc.core.JdbcTemplate db) {
      return args -> {
        for (String name :
            List.of(
                "V100__permission_data_details.sql", "V110__permission_selected_departments.sql")) {
          String sql =
              new String(
                  new org.springframework.core.io.ClassPathResource("db/migration/mysql/" + name)
                      .getContentAsByteArray(),
                  java.nio.charset.StandardCharsets.UTF_8);
          for (String q : sql.split(";"))
            if (!q.isBlank()) db.execute(q.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
        }
      };
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void administratorCanSaveDepartmentPresetThroughPermissionApi() throws Exception {
    String base = "/api/v1/enterprises/ent-acme";
    var g =
        mvc.perform(
                post(base + "/permission-groups")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content("{\"name\":\"Departments\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String gid =
        json.readTree(g.getResponse().getContentAsString()).path("data").path("groupId").asText();
    mvc.perform(
            post(base + "/permissions")
                .with(user("summer-user"))
                .with(csrf())
                .contentType("application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "code",
                            "dept-" + UUID.randomUUID(),
                            "name",
                            "Department read",
                            "type",
                            "DATA",
                            "groupId",
                            gid,
                            "dataScope",
                            Map.of(
                                "dataResourceKey",
                                "applications",
                                "action",
                                "read",
                                "scopePreset",
                                "DEPARTMENT")))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dataScope.scopePreset").value("DEPARTMENT"));
  }

  static final String BASE = "/api/v1/enterprises/ent-acme";

  void fixture() {
    for (String[] row :
        List.of(
            new String[] {"p07-A", "ent-acme", null},
            new String[] {"p07-B", "ent-acme", "p07-A"},
            new String[] {"p07-C", "ent-acme", "p07-B"},
            new String[] {"p07-X", "ent-acme", null},
            new String[] {"p07-foreign", "other", null})) {
      db.update(
          "insert into"
              + " summer_organization_unit(unit_id,enterprise_id,parent_id,unit_type,name,code,sort_order,status,created_at,updated_at)"
              + " values(?,?,?,'DEPARTMENT','Same"
              + " name','SAME',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          row[0],
          row[1],
          row[2]);
      db.update(
          "insert into"
              + " summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,department_id,business_visible)"
              + " values(?,?,?,?,?,?,true)",
          row[0],
          "ENTERPRISE:" + row[1],
          row[1],
          "someone",
          "summer-user",
          row[0]);
    }
    String member =
        db.queryForObject(
            "select member_id from summer_enterprise_member where enterprise_id='ent-acme' and"
                + " subject='summer-user'",
            String.class);
    db.update(
        "delete from summer_organization_membership where enterprise_id='ent-acme' and member_id=?",
        member);
    for (String id : List.of("p07-A", "p07-B"))
      db.update(
          "insert into"
              + " summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at)"
              + " values('ent-acme',?,?,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
          member,
          id);
  }

  String create(Map<String, Object> detail, int expected) throws Exception {
    var g =
        mvc.perform(
                post(BASE + "/permission-groups")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content("{\"name\":\"Departments\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String gid =
        json.readTree(g.getResponse().getContentAsString()).path("data").path("groupId").asText();
    var r =
        mvc.perform(
                post(BASE + "/permissions")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code",
                                "dept-" + UUID.randomUUID(),
                                "name",
                                "Scope",
                                "type",
                                "DATA",
                                "accessMode",
                                "PUBLIC",
                                "groupId",
                                gid,
                                "dataScope",
                                detail))))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(r.getResponse().getContentAsString())
        .path("data")
        .path("permissionId")
        .asText();
  }

  Map<String, Object> detail(String preset) {
    return Map.of("dataResourceKey", "applications", "action", "read", "scopePreset", preset);
  }

  void total(int count) throws Exception {
    for (String suffix : List.of("?size=1", "/jpa?size=1"))
      mvc.perform(get(BASE + "/data-records/applications" + suffix).with(user("summer-user")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.total").value(count));
  }

  @Test
  void allAssignmentsExpandDeduplicateAndIgnoreSelectedContext() throws Exception {
    fixture();
    create(detail("DEPARTMENT"), 201);
    total(2);
    create(detail("DEPARTMENT_AND_DESCENDANTS"), 201);
    total(3);
    db.update("delete from summer_organization_membership where enterprise_id='ent-acme'");
    total(0);
  }

  @Test
  void specifiedParentIsExactAndInactiveNodesStopContributing() throws Exception {
    fixture();
    var d = new HashMap<String, Object>(detail("SPECIFIED_DEPARTMENTS"));
    d.put("departmentIds", List.of("p07-A", "p07-A", "p07-X"));
    String id = create(d, 201);
    total(2);
    mvc.perform(get(BASE + "/permissions/" + id + "/data-scope").with(user("summer-user")))
        .andExpect(jsonPath("$.data.departmentIds.length()").value(2));
    db.update("update summer_organization_unit set status='DISABLED' where unit_id='p07-A'");
    total(1);
  }

  @Test
  void invalidDepartmentSelectionsAndOtherPresetListsFailAtomically() throws Exception {
    fixture();
    for (var ids : List.of(List.of(), List.of("p07-foreign"), List.of("missing"))) {
      var d = new HashMap<String, Object>(detail("SPECIFIED_DEPARTMENTS"));
      d.put("departmentIds", ids);
      create(d, 400);
    }
    var d = new HashMap<String, Object>(detail("SELF"));
    d.put("departmentIds", List.of());
    create(d, 400);
    total(0);
  }

  @Test
  void organizationMutationAdvancesDomainOnceAndReplayDoesNotAdvance() throws Exception {
    var auth =
        jwt()
            .jwt(
                j ->
                    j.subject("summer-user")
                        .claim(
                            "scope",
                            "enterprise.organization.write enterprise.roles.write"
                                + " enterprise.roles.read")
                        .claim(
                            "enterprise_memberships",
                            List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim(
                            "enterprise_capabilities",
                            Map.of(
                                "ent-acme",
                                List.of(
                                    "enterprise.organization.write",
                                    "enterprise.organization.read"))))
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "SCOPE_enterprise.organization.write"));
    openOrganizationCreate();
    Long before =
        db.queryForObject(
            "select revision from summer_authorization_domain where"
                + " domain_key='ENTERPRISE:ent-acme'",
            Long.class);
    String body =
        "{\"unitType\":\"DEPARTMENT\",\"name\":\"Revision department\",\"code\":\"REV07\"}";
    String key = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post(BASE + "/org-units")
                  .with(auth)
                  .contentType("application/json")
                  .header("Idempotency-Key", key)
                  .content(body))
          .andExpect(status().isCreated());
    org.junit.jupiter.api.Assertions.assertEquals(
        before + 1,
        db.queryForObject(
            "select revision from summer_authorization_domain where"
                + " domain_key='ENTERPRISE:ent-acme'",
            Long.class));
  }

  void openOrganizationCreate() throws Exception {
    var g =
        mvc.perform(
                post(BASE + "/permission-groups")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content("{\"name\":\"Organization API\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String gid =
        json.readTree(g.getResponse().getContentAsString()).path("data").path("groupId").asText();
    var permission =
        mvc.perform(
                post(BASE + "/permissions")
                    .with(user("summer-user"))
                    .with(csrf())
                    .contentType("application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "code",
                                "org-" + UUID.randomUUID(),
                                "name",
                                "Organization create",
                                "type",
                                "INTERFACE",
                                "accessMode",
                                "AUTHORIZED",
                                "groupId",
                                gid))))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        json.readTree(permission.getResponse().getContentAsString())
            .path("data")
            .path("permissionId")
            .asText();
    List<String> resources = new ArrayList<>();
    for (int page = 0; ; page++) {
      var r =
          mvc.perform(
                  get(BASE + "/authorization/interface-directory?page=" + page + "&size=100")
                      .with(user("summer-user")))
              .andExpect(status().isOk())
              .andReturn();
      var data = json.readTree(r.getResponse().getContentAsString()).path("data");
      for (var x : data.path("items"))
        if ("POST".equals(x.path("httpMethod").asText())
            && "/api/v1/enterprises/{enterpriseId}/org-units"
                .equals(x.path("normalizedPath").asText()))
          resources.add(x.path("resourceId").asText());
      if ((page + 1) * 100 >= data.path("total").asInt()) break;
    }
    org.junit.jupiter.api.Assertions.assertEquals(1, resources.size());
    String path = BASE + "/permissions/" + id + "/interfaces";
    var r = mvc.perform(get(path).with(user("summer-user"))).andExpect(status().isOk()).andReturn();
    mvc.perform(
            put(path)
                .with(user("summer-user"))
                .with(csrf())
                .contentType("application/json")
                .header("If-Match", r.getResponse().getHeader("ETag"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content(json.writeValueAsString(Map.of("resourceIds", resources))))
        .andExpect(status().isOk());
  }

  @Autowired com.abluepoint.summer.sample.member.MemberDirectory memberDirectory;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test
  void provisioningAdvancesOnlyEffectiveMembershipAndRollsBackWithItsCaller() {
    String domain = "ENTERPRISE:ent-acme";
    long before =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?",
            Long.class,
            domain);
    String id = "p07-provision-" + UUID.randomUUID();
    String subject = "p07-subject-" + UUID.randomUUID();
    var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    tx.execute(
        status ->
            memberDirectory.provision("ent-acme", id, subject, "Member", "member@example.test"));
    org.junit.jupiter.api.Assertions.assertEquals(
        before + 1,
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?",
            Long.class,
            domain));
    tx.execute(
        status ->
            memberDirectory.provision("ent-acme", id, subject, "Member", "member@example.test"));
    org.junit.jupiter.api.Assertions.assertEquals(
        before + 1,
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?",
            Long.class,
            domain));
    String rolledBack = "p07-rollback-" + UUID.randomUUID();
    tx.execute(
        status -> {
          memberDirectory.provision(
              "ent-acme", rolledBack, "rollback-" + subject, "Rollback", "rollback@example.test");
          status.setRollbackOnly();
          return null;
        });
    org.junit.jupiter.api.Assertions.assertTrue(
        memberDirectory.find("ent-acme", rolledBack).isEmpty());
    org.junit.jupiter.api.Assertions.assertEquals(
        before + 1,
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?",
            Long.class,
            domain));
  }
}
