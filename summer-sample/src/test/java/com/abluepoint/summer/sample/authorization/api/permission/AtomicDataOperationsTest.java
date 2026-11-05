package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.sample.authorization.application.data.DataOperationExecutor;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Public executor constraint seam; real HTTP consumer tests are separate. */
@SpringBootTest(properties="summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, PermissionDataHttpTest.DataSchema.class,
    PermissionDataOperationsHttpTest.OperationSchema.class})
@org.springframework.test.annotation.DirtiesContext
class AtomicDataOperationsTest {
  @Autowired DataOperationExecutor executor;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;

  @Test void laterActionFailureRollsBackAllEarlierDml() {
    var actor = new UsernamePasswordAuthenticationToken("summer-user", "unused", java.util.List.of());
    db.update("insert into summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible) values('atomic10','PLATFORM:platform','platform','summer-user','summer-user',true)");
    assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
      executor.write(Domain.platform(), "applications", Set.of("update","delete"), actor, scopes -> {
        assertEquals(Set.of("update","delete"), scopes.keySet());
        assertEquals(scopes.get("update").revision(), scopes.get("delete").revision());
        db.update("update summer_application_record set note='must roll back' where record_id='atomic10'");
        throw new org.springframework.security.access.AccessDeniedException("Later delete target is invisible");
      }));
    assertEquals("", db.queryForObject("select note from summer_application_record where record_id='atomic10'",String.class));
  }

  @Test void emptyOrNonWriteActionsCannotInvokeMutation() {
    var actor = new UsernamePasswordAuthenticationToken("summer-user", "unused", java.util.List.of());
    for (Set<String> actions : java.util.List.of(Set.<String>of(), Set.of("update","read"))) {
      assertThrows(com.abluepoint.summer.common.error.SummerException.class, () ->
        executor.write(Domain.platform(), "applications", actions, actor, scopes -> {
          fail("Invalid operation must not execute"); return null;
        }));
    }
  }

  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired tools.jackson.databind.ObjectMapper json;

  @Test void actionsUseIndependentScopesInsideOneTransaction() throws Exception {
    grant("update", "SELF");
    grant("delete", "TENANT_ALL");
    db.update("insert into summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible) values('mine10','PLATFORM:platform','platform','summer-user','summer-user',true),('other10','PLATFORM:platform','platform','other','summer-user',true)");
    var actor = new UsernamePasswordAuthenticationToken("summer-user", "unused", java.util.List.of());
    var sql = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db);
    executor.write(Domain.platform(), "applications", Set.of("update", "delete"), actor, scopes -> {
      var update = scopes.get("update");
      var delete = scopes.get("delete");
      assertEquals(1, sql.update("update summer_application_record set note='saved' where record_id in ('mine10','other10') and " + update.predicate(), update.bindings()));
      assertEquals(1, sql.update("delete from summer_application_record where record_id='other10' and " + delete.predicate(), delete.bindings()));
      assertThrows(UnsupportedOperationException.class, () -> scopes.clear());
      return true;
    });
    assertEquals("saved", db.queryForObject("select note from summer_application_record where record_id='mine10'",String.class));
    assertEquals(0, db.queryForObject("select count(*) from summer_application_record where record_id='other10'",Integer.class));
  }

  private void grant(String action, String preset) throws Exception {
    var user = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("summer-user");
    var group = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/platform/permission-groups")
        .with(user).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content("{\"name\":\"Atomic10\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
    String gid = json.readTree(group.getResponse().getContentAsString()).path("data").path("groupId").asText();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/platform/permissions")
        .with(user).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("code","atomic10-"+java.util.UUID.randomUUID(),"name","Atomic", "type","DATA", "accessMode","PUBLIC", "groupId",gid,"dataScope",java.util.Map.of("dataResourceKey","applications","action",action,"scopePreset",preset)))))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
  }
}
