package com.abluepoint.summer.sample.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
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
class TaskRetryRecoveryHttpTest {
    @Autowired MockMvc mockMvc;

    @Test
    void triggerIsIdempotentAndReplayedWithoutAnotherExecution() throws Exception {
        String taskId = create("Idempotent", "system.noop", 0, 30000);
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "trigger-once").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "trigger-once").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        awaitManualExecution(taskId);
        // Replay after completion as well: scheduled cron executions are independent of this key.
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "trigger-once").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        for (int i = 0; i < 20; i++) {
            org.assertj.core.api.Assertions.assertThat(manualExecutions(taskId)).hasSize(1);
            Thread.sleep(25);
        }
    }

    private void awaitManualExecution(String taskId) throws Exception {
        for (int i = 0; i < 80; i++) {
            List<Map<String, Object>> executions = manualExecutions(taskId);
            org.assertj.core.api.Assertions.assertThat(executions).hasSizeLessThanOrEqualTo(1);
            if (executions.size() == 1 && "SUCCESS".equals(executions.getFirst().get("status"))) return;
            Thread.sleep(25);
        }
        throw new AssertionError("idempotent trigger did not complete one execution");
    }

    private List<Map<String, Object>> manualExecutions(String taskId) throws Exception {
        var response = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/executions", taskId)
                        .with(admin("enterprise.tasks.read")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.items[?(@.idempotencyKey == 'trigger-once')]");
    }

    @Test
    void reusingTriggerKeyForAnotherTaskIsAConflict() throws Exception {
        String first = create("First", "system.noop", 0, 30000);
        String second = create("Second", "system.noop", 0, 30000);
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", first)
                        .header("Idempotency-Key", "shared-trigger").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", second)
                        .header("Idempotency-Key", "shared-trigger").with(admin("enterprise.tasks.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void failedTaskRecordsEachRetryAndEndsFailed() throws Exception {
        String taskId = create("Retrying", "system.fail", 2, 30000);
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "trigger-fail").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        for (int i = 0; i < 20; i++) {
            var result = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/executions", taskId)
                            .with(admin("enterprise.tasks.read"))).andReturn();
            List<Map<String, Object>> executions = JsonPath.read(result.getResponse().getContentAsString(),
                    "$.data.items[?(@.idempotencyKey == 'trigger-fail')]");
            if (executions.size() == 1 && "FAILED".equals(executions.getFirst().get("status"))
                    && "TASK_HANDLER_FAILED".equals(executions.getFirst().get("failureCode"))) {
                org.hamcrest.MatcherAssert.assertThat(result.getResponse().getContentAsString(), org.hamcrest.Matchers.containsString("\"attempt\":3"));
                org.hamcrest.MatcherAssert.assertThat(result.getResponse().getContentAsString(), org.hamcrest.Matchers.containsString("\"status\":\"FAILED\""));
                org.hamcrest.MatcherAssert.assertThat(result.getResponse().getContentAsString(), org.hamcrest.Matchers.containsString("\"attempts\":["));
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("task did not reach a final failed state");
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        String taskId = create("NeedsKey", "system.noop", 0, 30000);
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_IDEMPOTENCY_REQUIRED"));
    }

    @Test
    void timeoutIsRecordedAsFinalFailure() throws Exception {
        var result = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Timeout\",\"taskType\":\"system.sleep\",\"cronExpression\":\"0/5 * * * * ?\",\"parameters\":{\"millis\":1200},\"timeoutMs\":1000}")
                        .with(admin("enterprise.tasks.write"))).andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "trigger-timeout").with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());
        for (int i = 0; i < 80; i++) {
            var response = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/executions", taskId)
                    .with(admin("enterprise.tasks.read"))).andReturn().getResponse().getContentAsString();
            if (response.contains("TASK_TIMEOUT")) return;
            Thread.sleep(50);
        }
        throw new AssertionError("task did not reach timeout state");
    }

    private String create(String name, String type, int retries, long timeoutMs) throws Exception {
        var result = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"taskType\":\"" + type
                                + "\",\"cronExpression\":\"0/5 * * * * ?\",\"maxRetries\":" + retries
                                + ",\"timeoutMs\":" + timeoutMs + "}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.claim("scope", capability).subject("task-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
