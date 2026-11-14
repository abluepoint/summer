package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.security.storage.keys=memory")
@Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeCompositionHttpTest {
    static final String BASE = "/api/v1/enterprises/ent-acme";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired CurrentManagedAuthorizationTestFixture fixture;

    @BeforeEach void persistedSubjectAndRecords() {
        fixture.activeUser(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "alice");
        for (String owner : List.of("alice", "bob"))
            db.update("insert into summer_system_parameter(id,tenant_id,owner_id,created_by,parameter_key,value_type,value_json,required_flag,public_flag,status,version,created_at,updated_at) values (?,'ent-acme',?,'recorder',?,'STRING','\"safe\"',false,false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "composition-" + owner, owner, "composition." + owner);
    }

    @Test void currentPermissionHasStableReceiptAuditAndPersistedSubjectPreview() throws Exception {
        String key = "composition-self";
        String permission = create("SELF", key);
        String replay = create("SELF", key);
        assertEquals(permission, replay);
        assertEquals(1, db.queryForObject("select count(*) from summer_permission_mutation where domain_key='ENTERPRISE:ent-acme' and idempotency_key=? and length(fingerprint)=64", Integer.class, key));
        assertEquals(1, db.queryForObject("select count(*) from summer_authorization_audit where resource_id=? and action='PERMISSION_POST'", Integer.class, permission));
        assertEquals(1, db.queryForObject("select count(*) from summer_authorization_change_outbox where resource_id=?", Integer.class, permission));
        preview("alice", true);
        preview("bob", false);
    }

    @Test void currentPermissionsUnionAndRevocationReplaceOldDirectDenyAndRejectUnknownGrammar() throws Exception {
        create("SELF", "union-self");
        String broad = create("TENANT_ALL", "union-all");
        preview("bob", true);
        int oldPolicies = db.queryForObject("select count(*) from summer_data_scope_policy", Integer.class);
        int oldBindings = db.queryForObject("select count(*) from summer_data_scope_binding", Integer.class);
        mvc.perform(put(BASE + "/data-scopes/system-parameters").with(user("summer-user")).with(csrf())
            .contentType("application/json").content("{\"operator\":\"OR\",\"rules\":[{\"type\":\"TENANT\",\"effect\":\"ALLOW\"}]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/data-scopes/system-parameters/bindings/USER/alice").with(user("summer-user")).with(csrf())
            .contentType("application/json").content("{\"rules\":[{\"type\":\"TENANT\"}]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/data-scopes/system-parameters:preview").with(user("summer-user")).with(csrf())
            .contentType("application/json").content("{\"subject\":\"alice\"}"))
            .andExpect(status().isBadRequest());
        assertEquals(oldPolicies, db.queryForObject("select count(*) from summer_data_scope_policy", Integer.class));
        assertEquals(oldBindings, db.queryForObject("select count(*) from summer_data_scope_binding", Integer.class));
        preview("bob", true);
        mvc.perform(patch(BASE + "/permissions/" + broad).with(user("summer-user")).with(csrf())
            .header("If-Match", "\"1\"").header("Idempotency-Key", "disable-union-all")
            .contentType("application/json").content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());
        preview("bob", false);
        preview("alice", true);
        mvc.perform(post(BASE + "/permissions").with(user("summer-user")).with(csrf())
            .header("Idempotency-Key", "invalid-grammar").contentType("application/json")
            .content(json.writeValueAsString(body("RAW_SQL", "invalid-grammar"))))
            .andExpect(status().isBadRequest());
        assertEquals(0, db.queryForObject("select count(*) from summer_permission where code='invalid-grammar'", Integer.class));
    }

    private Map<String,Object> body(String preset, String code) {
        String group = db.queryForObject("select group_id from summer_permission_group where domain_key='ENTERPRISE:ent-acme' order by group_id limit 1", String.class);
        return Map.of("name", "Current DATA", "code", code, "groupId", group, "type", "DATA",
            "dataScope", Map.of("dataResourceKey", "system-parameters", "action", "read", "scopePreset", preset));
    }
    private String create(String preset, String key) throws Exception {
        var response = mvc.perform(post(BASE + "/permissions").with(user("summer-user")).with(csrf())
            .header("Idempotency-Key", key).contentType("application/json").content(json.writeValueAsString(body(preset, key))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        String id = json.readTree(response.getContentAsString()).path("data").path("permissionId").asText();
        String role = "composition-reader";
        if (db.queryForObject("select count(*) from summer_authorization_role where role_id=?", Integer.class, role) == 0) {
            db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at) values (?,'ent-acme','Composition','composition',false,'ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", role);
            db.update("insert into summer_permission_role values ('ENTERPRISE:ent-acme',?)", role);
            db.update("insert into summer_permission_user values ('ENTERPRISE:ent-acme','alice',1)");
            db.update("insert into summer_permission_user_role values ('ENTERPRISE:ent-acme','alice',?)", role);
        }
        db.update("insert into summer_role_permission select 'ENTERPRISE:ent-acme',?,? where not exists (select 1 from summer_role_permission where domain_key='ENTERPRISE:ent-acme' and role_id=? and permission_id=?)", role, id, role, id);
        return id;
    }
    private void preview(String owner, boolean allowed) throws Exception {
        mvc.perform(post(BASE + "/data-resources/system-parameters/preview").with(user("summer-user")).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(Map.of("subject", "alice", "recordId", "composition-" + owner, "action", "read"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(allowed));
    }
}
