package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@SpringBootTest(
        properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, PermissionDataHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext(
        classMode =
                org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PermissionDataHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;

    @org.springframework.boot.test.context.TestConfiguration
    static class DataSchema {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.ApplicationRunner dataSchema(
                org.springframework.jdbc.core.JdbcTemplate db) {
            return args -> {
                String sql =
                        new String(
                                new org.springframework.core.io.ClassPathResource(
                                                "db/migration/mysql/V100__permission_data_details.sql")
                                        .getContentAsByteArray(),
                                java.nio.charset.StandardCharsets.UTF_8);
                for (String q : sql.split(";"))
                    if (!q.isBlank())
                        db.execute(q.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
            };
        }
    }

    @Autowired HistoricalTenantPermissions historical;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void dataResponsesPreserveRequestCorrelation() throws Exception {
        mvc.perform(get("/api/v1/platform/permissions?type=INTERFACE").with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void completeDataPermissionCanBeCreatedAndRead() throws Exception {
        String base = "/api/v1/platform";
        var group =
                mvc.perform(
                                post(base + "/permission-groups")
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content("{\"name\":\"Data\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String gid =
                json.readTree(group.getResponse().getContentAsString())
                        .path("data")
                        .path("groupId")
                        .asText();
        mvc.perform(
                        post(base + "/permissions")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "code",
                                                        "data-" + UUID.randomUUID(),
                                                        "name",
                                                        "My applications",
                                                        "type",
                                                        "DATA",
                                                        "groupId",
                                                        gid,
                                                        "dataScope",
                                                        java.util.Map.of(
                                                                "interfacePermissionIds",
                                                                java.util.List.of(interfaceId()),
                                                                "scopePreset",
                                                                "SELF")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dataScope.scopePreset").value("SELF"));
    }

    @Test
    void noDataGrantReturnsEmptySqlPageBeforePagination() throws Exception {
        mvc.perform(
                        get("/api/v1/platform/data-records/applications?size=1")
                                .with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void jpaReadUsesSameEmptyScopeAsSql() throws Exception {
        mvc.perform(get("/api/v1/platform/data-records/applications/jpa").with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void applicantOwnershipTenantMaximumActionAndPreviewAgreeAcrossJpaAndSql() throws Exception {
        String base = "/api/v1/platform";
        for (var row :
                java.util.List.of(
                        new String[] {
                            "mine",
                            "PLATFORM:platform",
                            "platform",
                            "summer-user",
                            "someone",
                            "true"
                        },
                        new String[] {
                            "delegated",
                            "PLATFORM:platform",
                            "platform",
                            "someone",
                            "summer-user",
                            "true"
                        },
                        new String[] {
                            "foreign",
                            "PLATFORM:platform",
                            "other",
                            "summer-user",
                            "summer-user",
                            "true"
                        },
                        new String[] {
                            "capped",
                            "PLATFORM:platform",
                            "platform",
                            "summer-user",
                            "summer-user",
                            "false"
                        },
                        new String[] {
                            "domain",
                            "ENTERPRISE:platform",
                            "platform",
                            "summer-user",
                            "summer-user",
                            "true"
                        }))
            db.update(
                    "insert into"
                        + " summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible)"
                        + " values(?,?,?,?,?,?)",
                    row[0],
                    row[1],
                    row[2],
                    row[3],
                    row[4],
                    Boolean.valueOf(row[5]));
        var group =
                mvc.perform(
                                post(base + "/permission-groups")
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content("{\"name\":\"Read scope\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String gid =
                json.readTree(group.getResponse().getContentAsString())
                        .path("data")
                        .path("groupId")
                        .asText();
        mvc.perform(
                        post(base + "/permissions")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "code",
                                                        "unsupported-action",
                                                        "name",
                                                        "Unsupported",
                                                        "type",
                                                        "DATA",
                                                        "groupId",
                                                        gid,
                                                        "dataScope",
                                                        java.util.Map.of(
                                                                "dataResourceKey",
                                                                "applications",
                                                                "action",
                                                                "unregistered-action",
                                                                "scopePreset",
                                                                "TENANT_ALL")))))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/data-records/applications").with(user("summer-user")))
                .andExpect(jsonPath("$.data.total").value(0));
        String id = create(gid, "read", "SELF");
        for (String path :
                java.util.List.of(
                        "/data-records/applications?size=1",
                        "/data-records/applications/jpa?size=1"))
            mvc.perform(get(base + path).with(user("summer-user")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].recordId").value("mine"));
        mvc.perform(get(base + "/data-records/applications/delegated").with(user("summer-user")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(base + "/data-resources/applications/preview")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"subject\":\"summer-user\",\"recordId\":\"mine\",\"action\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true));
        mvc.perform(
                        post(base + "/data-resources/applications/preview")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"subject\":\"summer-user\",\"recordId\":\"mine\",\"action\":\"read\",\"roleAllows\":[\"TENANT_ALL\"]}"))
                .andExpect(status().isBadRequest());
        create(gid, "read", "TENANT_ALL");
        for (String path :
                java.util.List.of(
                        "/data-records/applications?size=1",
                        "/data-records/applications/jpa?size=1"))
            mvc.perform(get(base + path).with(user("summer-user")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void unsupportedActionsCannotBeConfiguredOrPreviewed() throws Exception {
        mvc.perform(
                        post("/api/v1/platform/data-resources/applications/preview")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"subject\":\"summer-user\",\"recordId\":\"mine\",\"action\":\"unregistered-action\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataScopeHasVersionedWholeReplacementEndpoint() throws Exception {
        var group =
                mvc.perform(
                                post("/api/v1/platform/permission-groups")
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content("{\"name\":\"Data details\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String gid =
                json.readTree(group.getResponse().getContentAsString())
                        .path("data")
                        .path("groupId")
                        .asText();
        String id = create(gid, "read", "SELF");
        String path = "/api/v1/platform/permissions/" + id + "/data-scope";
        var before =
                mvc.perform(get(path).with(user("summer-user")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.scopePreset").value("SELF"))
                        .andReturn();
        String etag = before.getResponse().getHeader("ETag");
        String body =
                json.writeValueAsString(java.util.Map.of("interfacePermissionIds", java.util.List.of(interfaceId()), "scopePreset", "TENANT_ALL"));
        String key = UUID.randomUUID().toString();
        var saved =
                mvc.perform(
                                put(path)
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header("If-Match", etag)
                                        .header("Idempotency-Key", key)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.scopePreset").value("TENANT_ALL"))
                        .andReturn();
        mvc.perform(
                        put(path)
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .header("If-Match", etag)
                                .header("Idempotency-Key", key)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", saved.getResponse().getHeader("ETag")));
        mvc.perform(
                        put(path)
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .header("If-Match", etag)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void linkedScopeFollowsOnlyAnApplicableInterfaceOnTheCurrentRequest() throws Exception {
        String base = "/api/v1/platform";
        String records = base + "/data-records/applications";
        var request = mvc.perform(get(records).with(user("summer-user")))
                .andExpect(status().isOk()).andReturn().getRequest();
        String resource = (String) request.getAttribute(
                com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess.RESOURCE_ATTRIBUTE);
        org.junit.jupiter.api.Assertions.assertNotNull(resource);
        String group = db.queryForObject("select group_id from summer_permission where domain_key='PLATFORM:platform' and code='PUBLIC'", String.class);
        var created = mvc.perform(post(base + "/permissions").with(user("summer-user")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("name", "Read applications", "code", "app-scope-target",
                        "groupId", group, "type", "INTERFACE", "accessMode", "AUTHORIZED"))))
                .andExpect(status().isCreated()).andReturn();
        String target = json.readTree(created.getResponse().getContentAsString()).path("data").path("permissionId").asText();
        String id = create(group, "read", "SELF");
        String path = base + "/permissions/" + id + "/data-scope";
        mvc.perform(get(path).with(user("summer-user")))
                .andExpect(jsonPath("$.data.interfacePermissionIds").isEmpty())
                .andExpect(jsonPath("$.data.dataResourceKey").doesNotExist())
                .andExpect(jsonPath("$.data.action").doesNotExist());
        mvc.perform(put(path).with(user("summer-user")).with(csrf()).header("If-Match", "\"1\"")
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"dataResourceKey\":\"applications\",\"action\":\"read\",\"scopePreset\":\"SELF\"}"))
                .andExpect(status().isBadRequest());
        for (String invalid : java.util.List.of(id, "unknown")) {
            mvc.perform(put(path).with(user("summer-user")).with(csrf()).header("If-Match", "\"1\"")
                    .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                    .content(json.writeValueAsString(java.util.Map.of("interfacePermissionIds", java.util.List.of(invalid), "scopePreset", "SELF"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(path).with(user("summer-user")).with(csrf()).header("If-Match", "\"1\"")
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("interfacePermissionIds", java.util.List.of(target, target), "scopePreset", "SELF"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.interfacePermissionIds.length()").value(1));
        mvc.perform(get(path).with(user("summer-user")))
                .andExpect(jsonPath("$.data.interfacePermissionIds[0]").value(target))
                .andExpect(jsonPath("$.data.dataResourceKey").doesNotExist());
        db.update("insert into summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible)"
                + " values('scope-row','PLATFORM:platform','platform','summer-user','summer-user',true)");
        db.update("insert into summer_permission_resource(domain_key,permission_id,resource_type,resource_id)"
                + " values('PLATFORM:platform',?,'INTERFACE',?)", target, resource);
        mvc.perform(get(records).with(user("summer-user"))).andExpect(jsonPath("$.data.total").value(0));
        db.update("insert into summer_role_permission(domain_key,role_id,permission_id)"
                + " select domain_key,role_id,? from summer_permission_user_role where domain_key='PLATFORM:platform' and username='summer-user'", target);
        mvc.perform(get(records).with(user("summer-user"))).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get(records + "/jpa").with(user("summer-user"))).andExpect(jsonPath("$.data.total").value(0));
        db.update("delete from summer_permission_resource where permission_id=? and resource_id=?", target, resource);
        mvc.perform(get(records).with(user("summer-user"))).andExpect(jsonPath("$.data.total").value(0));
    }

    private String create(String gid, String action, String preset) throws Exception {
        return historical.platformData(gid, action, preset, "PUBLIC");
    }

    private String interfaceId() {
        return db.queryForObject("select min(permission_id) from summer_permission where domain_key='PLATFORM:platform' and type='INTERFACE'", String.class);
    }
}
