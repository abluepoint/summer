package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({
    RolePermissionHttpTest.Schema.class,
    PermissionDataHttpTest.DataSchema.class,
    PermissionDataOperationsHttpTest.OperationSchema.class
})
@org.springframework.test.annotation.DirtiesContext(
        classMode =
                org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PermissionDataOperationsHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @Autowired tools.jackson.databind.ObjectMapper json;

    @org.springframework.boot.test.context.TestConfiguration
    static class OperationSchema {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.ApplicationRunner operationSchema(
                org.springframework.jdbc.core.JdbcTemplate db) {
            return args -> {
                db.execute(
                        "alter table summer_application_record add column if not exists note"
                            + " varchar(200) not null default ''");
                db.execute(
                        "alter table summer_application_record add column if not exists"
                            + " record_version bigint not null default 0");
            };
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void noCreateGrantDoesNotCreateARecord() throws Exception {
        mvc.perform(
                        post("/api/v1/platform/data-records/applications")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"recordId\":\"denied-create\",\"note\":\"sample\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/platform/data-records/applications/denied-create")
                                .with(user("summer-user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void writeActionUsesItsOwnScopeAndMixedBatchRollsBack() throws Exception {
        grant("read", "TENANT_ALL");
        grant("update", "SELF");
        fixture("mine", "summer-user");
        fixture("other", "someone");
        mvc.perform(
                        patch("/api/v1/platform/data-records/applications/other")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"version\":0,\"note\":\"denied\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        patch("/api/v1/platform/data-records/applications/mine")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"version\":0,\"note\":\"saved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(
                        post("/api/v1/platform/data-records/applications/batch-update")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"items\":[{\"recordId\":\"mine\",\"version\":1,\"note\":\"rollback\"},{\"recordId\":\"other\",\"version\":0,\"note\":\"denied\"}]}"))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(
                "saved",
                db.queryForObject(
                        "select note from summer_application_record where record_id='mine'",
                        String.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                1L,
                db.queryForObject(
                        "select record_version from summer_application_record where"
                            + " record_id='mine'",
                        Long.class));
    }

    void fixture(String id, String applicant) {
        db.update(
                "insert into"
                    + " summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible)"
                    + " values(?,'PLATFORM:platform','platform',?,'summer-user',true)",
                id,
                applicant);
    }

    @Test
    void batchExportAndCacheApplyCurrentActionScope() throws Exception {
        String read = grant("read", "SELF");
        grant("export", "TENANT_ALL");
        fixture("mine", "summer-user");
        fixture("other", "someone");
        fixture("capped", "summer-user");
        db.update(
                "update summer_application_record set business_visible=false where"
                    + " record_id='capped'");
        mvc.perform(
                        post("/api/v1/platform/data-records/applications/batch-read")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"recordIds\":[\"mine\",\"other\",\"capped\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].recordId").value("mine"));
        mvc.perform(
                        get("/api/v1/platform/data-records/applications/export")
                                .with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("recordId,applicantId,")))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("other")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("capped"))));
        mvc.perform(
                        get("/api/v1/platform/data-records/applications/mine/cached")
                                .with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value("mine"));
        db.update(
                "update summer_application_record set note='fresh',record_version=record_version+1"
                    + " where record_id='mine'");
        mvc.perform(
                        get("/api/v1/platform/data-records/applications/mine/cached")
                                .with(user("summer-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("fresh"));
        mvc.perform(
                        delete("/api/v1/platform/permissions/" + read)
                                .with(user("summer-user"))
                                .with(csrf())
                                .header("If-Match", "\"1\"")
                                .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/platform/data-records/applications/mine/cached")
                                .with(user("summer-user")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createFixesOwnershipAndRejectsForgedFields() throws Exception {
        grant("create", "SELF");
        mvc.perform(
                        post("/api/v1/platform/data-records/applications")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"recordId\":\"created\",\"note\":\"ok\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PLATFORM:platform/platform/summer-user/summer-user",
                db.queryForObject(
                        "select concat(domain_key,'/',tenant_id,'/',applicant_id,'/',created_by)"
                            + " from summer_application_record where record_id='created'",
                        String.class));
        for (String forged :
                java.util.List.of(
                        "\"tenantId\":\"foreign\"",
                        "\"businessVisible\":true",
                        "\"createdBy\":\"someone\"",
                        "\"departmentId\":\"foreign\"",
                        "\"applicantId\":\"unknown\""))
            mvc.perform(
                            post("/api/v1/platform/data-records/applications")
                                    .with(user("summer-user"))
                                    .with(csrf())
                                    .contentType("application/json")
                                    .content(
                                            "{\"recordId\":\"forged\",\"note\":\"no\","
                                                    + forged
                                                    + "}"))
                    .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                db.queryForObject("select count(*) from summer_application_record", Integer.class));
    }

    @Test
    void deleteAndStaleUpdateCannotPartiallySucceed() throws Exception {
        grant("delete", "SELF");
        grant("update", "SELF");
        fixture("mine", "summer-user");
        fixture("other", "someone");
        mvc.perform(
                        post("/api/v1/platform/data-records/applications/batch-delete")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"items\":[{\"recordId\":\"mine\",\"version\":0},{\"recordId\":\"other\",\"version\":0}]}"))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(
                2,
                db.queryForObject("select count(*) from summer_application_record", Integer.class));
        mvc.perform(
                        patch("/api/v1/platform/data-records/applications/mine")
                                .with(user("summer-user"))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"version\":1,\"note\":\"stale\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        delete("/api/v1/platform/data-records/applications/mine?version=0")
                                .with(user("summer-user"))
                                .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(
                        delete("/api/v1/platform/data-records/applications/other?version=0")
                                .with(user("summer-user"))
                                .with(csrf()))
                .andExpect(status().isNotFound());
    }

    String grant(String action, String preset) throws Exception {
        var group =
                mvc.perform(
                                post("/api/v1/platform/permission-groups")
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .content("{\"name\":\"Operations\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        String gid =
                json.readTree(group.getResponse().getContentAsString())
                        .path("data")
                        .path("groupId")
                        .asText();
        var permission =
                mvc.perform(
                                post("/api/v1/platform/permissions")
                                        .with(user("summer-user"))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .header(
                                                "Idempotency-Key",
                                                java.util.UUID.randomUUID().toString())
                                        .content(
                                                json.writeValueAsString(
                                                        java.util.Map.of(
                                                                "code",
                                                                "data-"
                                                                        + java.util.UUID
                                                                                .randomUUID(),
                                                                "name",
                                                                "Operation",
                                                                "type",
                                                                "DATA",
                                                                "accessMode",
                                                                "PUBLIC",
                                                                "groupId",
                                                                gid,
                                                                "dataScope",
                                                                java.util.Map.of(
                                                                        "dataResourceKey",
                                                                        "applications",
                                                                        "action",
                                                                        action,
                                                                        "scopePreset",
                                                                        preset)))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(permission.getResponse().getContentAsString())
                .path("data")
                .path("permissionId")
                .asText();
    }
}
