package com.abluepoint.summer.sample.statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.quartz.job-store-type=memory",
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StatisticsHttpTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedPersistentData() {
        Instant started = Instant.now().minusSeconds(120);
        jdbc.update("insert into summer_authorization_role(role_id, enterprise_id, name, code, built_in, status, capability_ids, capability_codes, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "role-stat", "ent-acme", "Statistics", "statistics", false, "ACTIVE", "[]", "[]", 1, started, started);
        jdbc.update("insert into summer_authorization_user_binding(binding_id, enterprise_id, username, role_ids, denied_capability_codes, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                "binding-stat", "ent-acme", "stats-user", "[]", "[]", 1, started, started);
        jdbc.update("insert into summer_dictionary_type(id, code, tenant_id, organization_id, created_by, owner_id, name, published, public_readable, enabled) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), "stats-dictionary", "ent-acme", "org-engineering", "stats-user", "stats-user", "Stats", true, true, true);
        jdbc.update("insert into summer_task_definition(task_id, enterprise_id, name, task_type, cron_expression, parameters_json, max_retries, timeout_ms, status, version, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "task-stat", "ent-acme", "Stats task", "system.noop", "0/5 * * * * ?", "{}", 0, 30_000, "ACTIVE", 0, started, started);
        jdbc.update("insert into summer_task_execution(execution_id, task_id, enterprise_id, fire_instance_id, idempotency_key, status, attempt, started_at, finished_at, failure_code, node_id, recovery_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "exec-stat-success", "task-stat", "ent-acme", "fire-stat-success", "key-stat-success", "SUCCESS", 1, started, started.plusSeconds(1), null, "test", 0);
        jdbc.update("insert into summer_task_execution(execution_id, task_id, enterprise_id, fire_instance_id, idempotency_key, status, attempt, started_at, finished_at, failure_code, node_id, recovery_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "exec-stat-failed", "task-stat", "ent-acme", "fire-stat-failed", "key-stat-failed", "FAILED", 1, started, started.plusSeconds(2), "TEST_FAILURE", "test", 0);
        jdbc.update("insert into aict_audit_event(event_id, event_type, enterprise_id, invocation_id, occurred_at, event_digest) values (?, ?, ?, ?, ?, ?)",
                "audit-stat", "STATISTICS_TEST", "ent-acme", "inv-stat", started, "digest-stat");
    }

    @Test
    void returnsWindowedTenantAndOrganizationScopedPersistentStatistics() throws Exception {
        String from = Instant.now().minusSeconds(600).toString();
        String to = Instant.now().plusSeconds(60).toString();
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/statistics")
                        .queryParam("from", from).queryParam("to", to).queryParam("organizationId", "org-engineering")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.window.from").value(from))
                .andExpect(jsonPath("$.data.window.to").value(to))
                .andExpect(jsonPath("$.data.scope.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.scope.organizationId").value("org-engineering"))
                .andExpect(jsonPath("$.data.userCount").value(1))
                .andExpect(jsonPath("$.data.roleCount").value(1))
                .andExpect(jsonPath("$.data.dictionaryCount").value(1))
                .andExpect(jsonPath("$.data.taskCount").value(1))
                .andExpect(jsonPath("$.data.taskExecutionCount").value(2))
                .andExpect(jsonPath("$.data.successfulTaskExecutionCount").value(1))
                .andExpect(jsonPath("$.data.failedTaskExecutionCount").value(1))
                .andExpect(jsonPath("$.data.taskSuccessRate").value(0.5))
                .andExpect(jsonPath("$.data.auditEventCount").value(1));
    }

    @Test
    void rejectsMissingCapabilityAndInvalidWindow() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/statistics").with(jwt()
                        .jwt(token -> token.subject("ordinary").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/statistics")
                        .queryParam("from", "2026-01-02T00:00:00Z")
                        .queryParam("to", "2026-01-01T00:00:00Z")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_STATISTICS_INVALID"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
                return jwt().jwt(token -> token.subject("stats-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("organization_id", "org-engineering")
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.statistics.read"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_enterprise.statistics.read"));
    }
}
