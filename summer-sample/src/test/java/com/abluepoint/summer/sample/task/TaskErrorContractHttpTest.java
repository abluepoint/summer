package com.abluepoint.summer.sample.task;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.quartz.job-store-type=memory")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskErrorContractHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.interfaces("ENTERPRISE:ent-acme", "task-admin", java.util.Set.of("tasks"));
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() { managed.close(); }

    @Autowired MockMvc mvc;

    @Test
    void invalidAndUntrustedRequestsKeepTheirPublicMessages() throws Exception {
        failure(post("/api/v1/enterprises/ent-acme/tasks").contentType(MediaType.APPLICATION_JSON).content("{}"),
                400, "SUMMER_TASK_INVALID", "Task name is required");
        failure(post("/api/v1/enterprises/ent-acme/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"untrusted\",\"taskType\":\"shell.exec\",\"cronExpression\":\"invalid\"}"),
                400, "SUMMER_TASK_TYPE_UNTRUSTED", "Task type is not trusted: shell.exec");
    }

    @Test
    void missingTasksAndStreamCursorsRetainTheirEntrySpecificContract() throws Exception {
        String missing = "/api/v1/enterprises/ent-acme/tasks/absent-unified-task";
        failure(get(missing + "/executions"), 404, "SUMMER_NOT_FOUND", "Task not found");
        failure(patch(missing).contentType(MediaType.APPLICATION_JSON).content("{}"),
                404, "SUMMER_NOT_FOUND", "Task not found");
        failure(post(missing + ":trigger"), 404, "SUMMER_NOT_FOUND", "Task not found");
        failure(post(missing + ":pause"), 404, "SUMMER_NOT_FOUND", "Task not found");
        failure(post(missing + ":resume"), 404, "SUMMER_NOT_FOUND", "Task not found");
        failure(get(missing + "/events").accept(MediaType.TEXT_EVENT_STREAM),
                404, "SUMMER_NOT_FOUND", "Task was not found");
        failure(get(missing + "/events").accept(MediaType.TEXT_EVENT_STREAM).header("Last-Event-ID", "-1"),
                400, "SUMMER_TASK_SSE_LAST_EVENT_ID", "Last-Event-ID must be a non-negative integer");
    }

    @Test
    void invalidUpdatesKeepThe400ContractAndLeaveTheDefinitionActive() throws Exception {
        String task = create();
        failure(patch("/api/v1/enterprises/ent-acme/tasks/" + task).header("If-Match", version(task))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cronExpression\":\"invalid\"}"),
                400, "SUMMER_TASK_INVALID", "Task name or cron expression is invalid");
    }

    @Test
    void staleUpdatesPauseAndResumeKeepThe412ContractAndDoNotChangeTheTask() throws Exception {
        String task = create();
        String path = "/api/v1/enterprises/ent-acme/tasks/" + task;
        failure(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"must not be applied\"}"),
                412, "SUMMER_PRECONDITION_FAILED", "The task was changed by another request");
        failure(post(path + ":pause").header("If-Match", "\"-1\""),
                412, "SUMMER_PRECONDITION_FAILED", "The task was changed by another request");
        failure(post(path + ":resume"), 412, "SUMMER_PRECONDITION_FAILED", "The task was changed by another request");
        var response = mvc.perform(get("/api/v1/enterprises/ent-acme/tasks").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(response, "$.data.items[?(@.taskId == '" + task + "')].status");
        org.assertj.core.api.Assertions.assertThat(statuses).containsExactly("ACTIVE");
    }

    @Test
    void pausedTriggersAndIdempotencyErrorsKeepTheirSpecificMessages() throws Exception {
        String first = create();
        String second = create();
        String path = "/api/v1/enterprises/ent-acme/tasks/";
        failure(post(path + first + ":trigger"), 400, "SUMMER_IDEMPOTENCY_REQUIRED", "Idempotency-Key is required");
        String key = UUID.randomUUID().toString();
        mvc.perform(post(path + first + ":trigger").header("Idempotency-Key", key).with(admin()))
                .andExpect(status().isAccepted());
        failure(post(path + second + ":trigger").header("Idempotency-Key", key),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another task trigger");
        mvc.perform(post(path + second + ":pause").header("If-Match", version(second)).with(admin()))
                .andExpect(status().isOk());
        failure(post(path + second + ":trigger").header("Idempotency-Key", "paused"),
                409, "SUMMER_TASK_CONFLICT", "Paused task cannot be triggered");
    }

    private String version(String task) throws Exception {
        var response = mvc.perform(get("/api/v1/enterprises/ent-acme/tasks").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> versions = JsonPath.read(response, "$.data.items[?(@.taskId == '" + task + "')].version");
        return "\"" + versions.getFirst().longValue() + "\"";
    }

    private String create() throws Exception {
        var result = mvc.perform(post("/api/v1/enterprises/ent-acme/tasks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + UUID.randomUUID() + "\",\"taskType\":\"system.noop\",\"cronExpression\":\"0 0 0 1 1 ? 2099\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.taskId");
    }

    private ResultActions failure(MockHttpServletRequestBuilder request, int statusCode, String code, String message) throws Exception {
        return mvc.perform(request.with(admin()).header("X-Request-Id", "task-error-contract"))
                .andExpect(status().is(statusCode))
                .andExpect(header().string("X-Request-Id", "task-error-contract"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.violations").isEmpty())
                .andExpect(jsonPath("$.requestId").value("task-error-contract"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("scope", "enterprise.tasks.write enterprise.tasks.read").subject("task-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.tasks.write", "enterprise.tasks.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.tasks.write"), new SimpleGrantedAuthority("SCOPE_enterprise.tasks.read"));
    }
}
