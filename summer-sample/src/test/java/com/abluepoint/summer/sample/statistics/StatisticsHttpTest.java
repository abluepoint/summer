package com.abluepoint.summer.sample.statistics;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class StatisticsHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @Autowired org.springframework.transaction.PlatformTransactionManager grantTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources grantResources;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture dataGrants;

    @org.junit.jupiter.api.AfterEach
    void releaseCurrentDataGrants() { if (dataGrants != null) dataGrants.close(); }
    private String statisticsRole;
    private String rolesRole;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    private static final String URL = "/api/v1/enterprises/ent-acme/statistics";
    private static final List<String> CODES = List.of("enterprise.statistics.read", "enterprise.members.read", "enterprise.roles.read", "platform.dictionary.read", "enterprise.tasks.read", "enterprise.audit.read");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void seedPersistentData() {
        jdbc.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values (?,?,?,?,?,?,?,?,?)", "ent-acme","member-stat","stats-admin","Statistics","stats@example.test","ACTIVE",0,START,START);
        jdbc.update("insert into summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values (?,?,?,?,?,?)", "ent-acme","member-stat","org-engineering",true,START,START);
        jdbc.update("insert into summer_organization_context(subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,?,?,?,?,?)", "stats-admin","ent-acme","org-engineering","1",START,START);
        jdbc.update("insert into summer_dictionary_type(id,code,tenant_id,organization_id,created_by,owner_id,name,published,public_readable,enabled) values (?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID().toString(),"stats-dictionary","platform",null,"other-user","other-user","Stats",true,true,true);
        jdbc.update("insert into summer_task_definition(task_id,enterprise_id,name,task_type,cron_expression,parameters_json,max_retries,timeout_ms,status,version,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?)", "task-stat","ent-acme","Stats task","system.noop","0 0 0 1 1 ? 2099","{}",0,30000,"ACTIVE",0,START,START);
        for (String state : List.of("SUCCESS","FAILED","RUNNING")) jdbc.update("insert into summer_task_execution(execution_id,task_id,enterprise_id,fire_instance_id,idempotency_key,status,attempt,started_at,finished_at,node_id,recovery_count) values (?,?,?,?,?,?,?,?,?,?,?)", "exec-"+state,"task-stat","ent-acme","fire-"+state,"key-"+state,state,1,START.plusSeconds(1),state.equals("RUNNING")?null:START.plusSeconds(2),"test",0);
        jdbc.update("insert into summer_audit_event(event_id,tenant_id,organization_id,principal,resource,action,result,request_id,detail_json,occurred_at) values (?,?,?,?,?,?,?,?,?,?)", "audit-stat","ent-acme","org-engineering","stats-admin","task:task-stat","TASK_CREATED","SUCCESS","stat-request","{}",START.plusSeconds(1));
        dataGrants = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(jdbc, grantTransactions, grantResources);
        dataGrants.data("ent-acme", "stats-admin", "dictionaries", "read", "TENANT_ALL");
        statisticsRole = current.grantHandlers(Domain.enterprise("ent-acme"), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.statistics.StatisticsController")
                        && java.util.Set.of("GET /api/v1/enterprises/{enterpriseId}/statistics").contains(method + " " + path));
        rolesRole = current.grantHandlers(Domain.enterprise("ent-acme"), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController")
                        && java.util.Set.of("GET /api/v1/enterprises/{enterpriseId}/roles").contains(method + " " + path));
        current.grantHandlers(Domain.enterprise("ent-acme"), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.member.MemberController")
                        && java.util.Set.of("GET /api/v1/enterprises/{enterpriseId}/members").contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                        && java.util.Set.of("GET /api/v1/platform/dictionaries").contains(method + " " + path));
        current.grantHandlers(Domain.enterprise("ent-acme"), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.task.TaskSchedulerController")
                        && java.util.Set.of("GET /api/v1/enterprises/{enterpriseId}/tasks").contains(method + " " + path));
        current.grantHandlers(Domain.enterprise("ent-acme"), "stats-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.auditledger.AuditLedgerController")
                        && java.util.Set.of("GET /api/v1/enterprises/{enterpriseId}/audit-events").contains(method + " " + path));
    }
    @Test void returnsBusinessAuditAndExecutionCountsFromPersistence() throws Exception {
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.auditEventCount").value(1))
            .andExpect(jsonPath("$.data.dictionaryCount").value(1))
            .andExpect(jsonPath("$.data.taskExecutionCount").value(3)).andExpect(jsonPath("$.data.successfulTaskExecutionCount").value(1))
            .andExpect(jsonPath("$.data.failedTaskExecutionCount").value(1)).andExpect(jsonPath("$.data.taskSuccessRate").value(1.0/3.0));
    }
    @Test void countsOrganizationMembersWithoutRequiringRoleBindings() throws Exception {
        // Alice and stats-admin both have engineering membership; only the latter has a role binding.
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from summer_permission_user_role where domain_key=? and username=?",
                Integer.class, "ENTERPRISE:ent-acme", "alice")).isZero();
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.userCount").value(2));
    }
    @Test void appliesTheSameSelfDataScopeAsDictionaryReads() throws Exception {
        dataGrants.data("ent-acme", "stats-admin", "dictionaries", "read", "SELF");
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.dictionaryCount").value(0));
    }
    @Test void oldJwtDoesNotKeepRevokedInterfaceOrMetricAccess() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        long before = mapper.readTree(query().andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/accessibleInterfaceCount").asLong();
        current.revoke(Domain.enterprise("ent-acme"), "stats-admin", rolesRole);
        var response = query().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(response).at("/data/accessibleInterfaceCount").asLong()).isLessThan(before);
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(response).at("/data/roleCount").isNull()).isTrue();
    }
    @Test void oldJwtCannotReadStatisticsAfterCapabilityRevocation() throws Exception {
        current.revoke(Domain.enterprise("ent-acme"), "stats-admin", statisticsRole);
        query().andExpect(status().isForbidden());
    }
    @Test void revokedOrganizationMembershipFailsClosedWithOldJwt() throws Exception {
        jdbc.update("delete from summer_organization_membership where member_id='member-stat'");
        query().andExpect(status().isForbidden());
    }
    @Test void defaultFromIsRelativeToExplicitTo() throws Exception {
        mockMvc.perform(get(URL).queryParam("to","2025-02-01T00:00:00Z").with(admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.window.from").value("2025-01-02T00:00:00Z"));
    }
    @Test void reportsBothMalformedTimeFields() throws Exception {
        mockMvc.perform(get(URL).queryParam("from", "yesterday").queryParam("to", "today").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.violations[*].field",
                    org.hamcrest.Matchers.containsInAnyOrder("from", "to")));
    }
    @Test void rejectsUnknownParameters() throws Exception {
        mockMvc.perform(get(URL).queryParam("typo","1").with(admin()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"));
    }
    @ParameterizedTest(name = "invalid window from={0} to={1}")
    @CsvSource({
        "2026-01-02T00:00:00Z,2026-01-01T00:00:00Z",
        "2024-01-01T00:00:00Z,2026-01-01T00:00:00Z",
        "not-an-instant,2026-01-01T00:00:00Z"
    })
    void invalidWindowsUseFrameworkInvalidArgument(String from, String to) throws Exception {
        var response = mockMvc.perform(get(URL).queryParam("from",from).queryParam("to",to)
                .header("X-Request-Id","statistics invalid:/window").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
            .andExpect(jsonPath("$.requestId").value("statistics_invalid__window"))
            .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(response.getHeader("X-Request-Id")).isEqualTo("statistics_invalid__window");
    }
    private org.springframework.test.web.servlet.ResultActions query() throws Exception {
        return mockMvc.perform(get(URL).queryParam("from",START.toString()).queryParam("to",START.plusSeconds(60).toString()).with(admin()));
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("scope", CODES.stream().filter(value -> !value.contains(":")).collect(java.util.stream.Collectors.joining(" "))).subject("stats-admin")
            .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
            .claim("organization_context",Map.of("enterpriseId","ent-acme","organizationId","org-engineering"))
            .claim("enterprise_capabilities",Map.of("ent-acme",CODES)))
            .authorities(CODES.stream().filter(c -> !c.contains(":")).map(c -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_"+c)).toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }
}
