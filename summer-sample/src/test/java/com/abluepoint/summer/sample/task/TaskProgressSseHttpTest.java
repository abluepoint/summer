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

import static org.hamcrest.Matchers.containsString;
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
class TaskProgressSseHttpTest {
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

    @Autowired MockMvc mockMvc;
    @Autowired TaskProgressEventRepository events;
    @Autowired TaskExecutionService executionService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void revokedReadPermissionClosesTheStreamBeforeNewTaskEventsAreSent() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Revocation\",\"taskType\":\"system.noop\",\"cronExpression\":\"0 0 0 1 1 ? 2099\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");
        var stream = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/events", taskId)
                        .accept(MediaType.TEXT_EVENT_STREAM).with(admin("enterprise.tasks.read")))
                .andExpect(request().asyncStarted()).andReturn();
        managed.revokeInterfaces("ENTERPRISE:ent-acme", "task-admin");
        executionService.execute(taskId, "revocation-fire");
        stream.getAsyncResult(3000);
        org.assertj.core.api.Assertions.assertThat(stream.getResponse().getContentAsString())
                .doesNotContain("execution.started", "execution.succeeded");
    }

    @Test
    void invalidCursorReturnsJsonFailureForAnEventStreamClient() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/task-1/events")
                        .header("Last-Event-ID", "-1").accept(MediaType.TEXT_EVENT_STREAM)
                        .with(admin("enterprise.tasks.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("SUMMER_TASK_SSE_LAST_EVENT_ID"));
    }

    @Test
    void taskExecutionPublishesProgressWithoutSensitiveParameters() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Progress\",\"taskType\":\"system.fail\",\"cronExpression\":\"0/5 * * * * ?\",\"parameters\":{\"secret\":\"do-not-stream\"},\"maxRetries\":1}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "progress-trigger")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());

        for (int attempt = 0; attempt < 40; attempt++) {
            if (events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc("ent-acme", taskId).stream()
                    .anyMatch(event -> "execution.failed".equals(event.eventType()))) break;
            Thread.sleep(25);
        }
        var published = events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc("ent-acme", taskId);
        org.assertj.core.api.Assertions.assertThat(published).extracting(TaskProgressEventEntity::eventType)
                .contains("execution.started", "execution.attempt", "execution.log", "execution.failed");
        org.assertj.core.api.Assertions.assertThat(published).allMatch(event -> !event.payloadJson().contains("do-not-stream"));
        org.assertj.core.api.Assertions.assertThat(published).isSortedAccordingTo(java.util.Comparator.comparing(TaskProgressEventEntity::eventId));
    }

    @Test
    void progressStreamRequiresCapabilityAndTenantMembership() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/task-1/events").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/tasks/task-1/events").with(admin("enterprise.tasks.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void progressStreamUsesSseAndAcceptsLastEventIdForReplay() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stream\",\"taskType\":\"system.noop\",\"cronExpression\":\"0/5 * * * * ?\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");
        var result = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/events", taskId)
                        .header("Last-Event-ID", "17")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(admin("enterprise.tasks.read")))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getRequest().getAsyncContext()).isNotNull();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return TaskAuthenticationFixture.cookie(capability);
    }
}
