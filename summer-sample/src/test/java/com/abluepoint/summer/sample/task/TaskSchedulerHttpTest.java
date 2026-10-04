package com.abluepoint.summer.sample.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.quartz.job-store-type=memory",
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskSchedulerHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsPausesResumesTriggersAndPersistsExecutionHistory() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .header("X-Request-Id", "task-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Heartbeat","taskType":"system.noop",
                                 "cronExpression":"0/5 * * * * ?","parameters":{"label":"safe"},"maxRetries":1}
                                """)
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "task-create"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("system.noop"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.parameters.label").value("safe"))
                .andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");
        String etag = created.getResponse().getHeader("ETag");

        var paused = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:pause", taskId)
                        .header("If-Match", etag).header("X-Request-Id", "task-pause")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andReturn();
        String pausedEtag = paused.getResponse().getHeader("ETag");

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:resume", taskId)
                        .header("If-Match", pausedEtag).header("X-Request-Id", "task-resume")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "task-trigger-1").header("X-Request-Id", "task-trigger")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(taskId));

        MvcResult executions = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            executions = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/executions", taskId)
                            .with(admin("enterprise.tasks.read")))
                    .andExpect(status().isOk()).andReturn();
            List<String> statuses = JsonPath.read(executions.getResponse().getContentAsString(), "$.data.items[*].status");
            if (statuses.contains("SUCCESS")) break;
            Thread.sleep(50);
        }
        assertThat(JsonPath.read(executions.getResponse().getContentAsString(), "$.data.items[*].status"), hasItem("SUCCESS"));

        mockMvc.perform(get("/api/audit-events").queryParam("resource", "task:" + taskId)
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("TASK_PAUSED")))
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("TASK_RESUMED")))
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("TASK_TRIGGERED")));
    }

    @Test
    void rejectsUntrustedTaskTypesAndInvalidCron() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"taskType\":\"shell.exec\",\"cronExpression\":\"0/5 * * * * ?\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_TASK_TYPE_UNTRUSTED"));

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"taskType\":\"system.noop\",\"cronExpression\":\"invalid\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_TASK_INVALID"));
    }

    @Test
    void protectsTaskApiAndTenantBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/tasks").with(admin("enterprise.tasks.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("task-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.subject("auditor")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_enterprise.audit.read"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"));
    }
}
