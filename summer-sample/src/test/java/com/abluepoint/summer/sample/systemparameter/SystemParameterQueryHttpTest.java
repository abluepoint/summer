package com.abluepoint.summer.sample.systemparameter;

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

@SpringBootTest(properties = "summer.security.storage.keys=memory")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemParameterQueryHttpTest {
    // These legacy fixtures now declare their enterprise; production has no implicit sample tenant.
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
                .andExpect(status().isOk())
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
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("reader"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    }
}
