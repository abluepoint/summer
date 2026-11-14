package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.resilience.timeout=5s"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class SystemParameterQueryHttpTest {
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
        current.grantHandlers(Domain.enterprise("ent-acme"), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of("POST /api/system-parameters").contains(method + " " + path));
        dataGrants.data("ent-acme", "reader", "system-parameters", "read", "TENANT_ALL");
        current.grantHandlers(Domain.enterprise("ent-acme"), "reader", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of(
                                "GET /api/system-parameters",
                                "GET /api/system-parameters/{key}")
                        .contains(method + " " + path));
    }

    // The token declares its enterprise; authorization uses current persisted facts.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }


    @Autowired
    MockMvc mockMvc;


    @Test
    void filtersSortsPagesAndProjectsParametersInsideTheTenantBoundary() throws Exception {
        createParameter("orders.page-size", "INTEGER", "25");
        createParameter("orders.currency", "STRING", "CNY");
        createParameter("feature.checkout", "BOOLEAN", "true");

        mockMvc.perform(get("/api/system-parameters")
                        .queryParam("key", "orders")
                        .queryParam("type", "STRING")
                        .queryParam("status", "ACTIVE")
                        .queryParam("sort", "key")
                        .queryParam("direction", "asc")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .queryParam("projection", "summary")
                        .queryParam("tenantId", "ent-acme")
                        .header("X-Request-Id", "parameter-query")
                        .with(reader()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "parameter-query"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.projection").value("summary"))
                .andExpect(jsonPath("$.data.items[0].key").value("orders.currency"))
                .andExpect(jsonPath("$.data.items[0].value").doesNotExist());

        mockMvc.perform(get("/api/system-parameters/orders.currency").with(reader()))
                .andExpect(result -> {
                    var failure = result.getResolvedException();
                    org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                            .as("Current parameter detail positive; resolved exception: %s; cause: %s",
                                    failure, failure == null ? null : failure.getCause())
                            .isEqualTo(200);
                })
                .andExpect(jsonPath("$.data.key").value("orders.currency"))
                .andExpect(jsonPath("$.data.value").value("CNY"));
    }

    @Test
    void rejectsUnknownSortAndTenantQueriesAtTheHttpBoundary() throws Exception {
        mockMvc.perform(get("/api/system-parameters")
                        .queryParam("sort", "valueJson")
                        .header("X-Request-Id", "parameter-invalid-sort")
                        .with(reader()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "parameter-invalid-sort"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"));

        mockMvc.perform(get("/api/system-parameters")
                        .queryParam("tenantId", "ent-other")
                        .header("X-Request-Id", "parameter-forbidden-tenant")
                        .with(reader()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Request-Id", "parameter-forbidden-tenant"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        mockMvc.perform(get("/api/system-parameters").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    private void createParameter(String key, String type, String value) throws Exception {
        String jsonValue = "INTEGER".equals(type) ? value : "BOOLEAN".equals(type) ? value : "\"" + value + "\"";
        mockMvc.perform(post("/api/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + key + "\",\"type\":\"" + type + "\",\"value\":" + jsonValue + "}")
                        .with(writer()))
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "system.parameter.read").subject("reader"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "system.parameter.write").subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    }
}
