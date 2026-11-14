package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class SystemParameterHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @Autowired org.springframework.jdbc.core.JdbcTemplate grantDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager grantTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources grantResources;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture dataGrants;

    @org.junit.jupiter.api.AfterEach
    void releaseCurrentDataGrants() { if (dataGrants != null) dataGrants.close(); }

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        dataGrants = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(grantDb, grantTransactions, grantResources);
        dataGrants.data("ent-acme", "operator", "system-parameters", "create", "TENANT_ALL");
        dataGrants.data("ent-acme", "operator", "system-parameters", "update", "TENANT_ALL");
        current.grantHandlers(Domain.enterprise("ent-acme"), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of(
                                "POST /api/system-parameters",
                                "PATCH /api/system-parameters/{key}",
                                "POST /api/system-parameters/{key}/enable")
                        .contains(method + " " + path));
        dataGrants.data("ent-acme", "reader", "system-parameters", "read", "TENANT_ALL");
        current.grantHandlers(Domain.enterprise("ent-acme"), "reader", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of("GET /api/system-parameters").contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "auditor", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && java.util.Set.of("GET /api/audit-events").contains(method + " " + path));
    }

    // The token declares its enterprise; authorization uses current persisted facts.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }


    @Autowired
    MockMvc mockMvc;

    @Test
    void createsListsAndUpdatesTypedParameterWithOptimisticVersion() throws Exception {
        mockMvc.perform(post("/api/system-parameters")
                        .header("X-Request-Id", "parameter-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"orders.page-size","type":"INTEGER","value":25,
                                 "required":true,"min":1,"max":100,"public":false,
                                 "description":"Page size"}
                                """)
                        .with(writer()))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "parameter-create"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.key").value("orders.page-size"))
                .andExpect(jsonPath("$.data.type").value("INTEGER"))
                .andExpect(jsonPath("$.data.value").value(25))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/system-parameters")
                        .queryParam("type", "INTEGER")
                        .with(reader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].key").value("orders.page-size"));

        mockMvc.perform(patch("/api/system-parameters/orders.page-size")
                        .header("X-Request-Id", "parameter-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":50,"version":0,"status":"INACTIVE"}
                                """)
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(50))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(patch("/api/system-parameters/orders.page-size")
                        .header("X-Request-Id", "parameter-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":75,"version":0}
                                """)
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "parameter-stale"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PARAMETER_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("parameter-stale"));

        mockMvc.perform(post("/api/system-parameters/orders.page-size/enable")
                        .header("X-Request-Id", "parameter-enable")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("resource", "system-parameter:orders.page-size")
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("SYSTEM_PARAMETER_CREATED")))
                .andExpect(jsonPath("$.data.items[*].action").value(hasItem("SYSTEM_PARAMETER_UPDATED")));
    }

    @Test
    void rejectsInvalidTypedValueWithFieldViolationAndProtectsWriteEndpoint() throws Exception {
        mockMvc.perform(post("/api/system-parameters")
                        .header("X-Request-Id", "parameter-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"orders.page-size","type":"INTEGER","value":101,
                                 "required":true,"min":1,"max":100}
                                """)
                        .with(writer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PARAMETER_INVALID"))
                .andExpect(jsonPath("$.error.violations[*].field").value(hasItem("value")))
                .andExpect(jsonPath("$.requestId").value("parameter-invalid"));

        mockMvc.perform(get("/api/system-parameters").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return jwt().jwt(token -> token.claim("scope", "system.parameter.read").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("reader"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("scope", "system.parameter.write").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.claim("scope", "enterprise.audit.read customer.read").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("auditor"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"),
                        new SimpleGrantedAuthority("SCOPE_customer.read"));
    }
}
