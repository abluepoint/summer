package com.abluepoint.summer.sample.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerAuditLedgerHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void customerWritesAppendQueryableTenantBoundAuditEvents() throws Exception {
        String username = "audit.customer";
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-create")
                        .content("{\"username\":\"" + username + "\",\"email\":\"audit.customer@summer.local\",\"displayName\":\"Audit Customer\"}")
                        .with(customerWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{username}", username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-update")
                        .content("{\"displayName\":\"Renamed Customer\"}")
                        .with(customerWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{username}", username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-deactivate")
                        .content("{\"active\":false}")
                        .with(customerWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{username}", username)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-reactivate")
                        .content("{\"active\":true}")
                        .with(customerWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/customers/{username}", username)
                        .header("X-Request-Id", "req-delete")
                        .with(customerWriter()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("resource", "customer:" + username)
                        .queryParam("size", "10")
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.items[*].action").value(containsInAnyOrder(
                        "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_DEACTIVATED",
                        "CUSTOMER_REACTIVATED", "CUSTOMER_DELETED")))
                .andExpect(jsonPath("$.data.items[0].tenantId").value("ent-acme"))
                .andExpect(jsonPath("$.data.items[0].principal").value("operator"))
                .andExpect(jsonPath("$.data.items[*].requestId").value(hasItem("req-create")))
                .andExpect(jsonPath("$.data.items[0].detail.email").doesNotExist());

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("action", "CUSTOMER_DEACTIVATED")
                        .queryParam("requestId", "req-deactivate")
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCESS"));

        mockMvc.perform(get("/api/audit-events/export")
                        .queryParam("resource", "customer:" + username)
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("eventId,tenantId")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("audit.customer@summer.local"))));
    }

    @Test
    void auditQueryRequiresReadCapabilityAndDoesNotAllowCrossTenantSelection() throws Exception {
        mockMvc.perform(get("/api/audit-events").with(customerWriter()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("tenantId", "other-tenant")
                        .with(auditReader()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectedCustomerWriteIsRecordedWithoutPersistingSensitiveInput() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audit.failure\",\"email\":\"audit.failure@summer.local\",\"displayName\":\"Failure\"}")
                        .with(customerWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-failure")
                        .content("{\"username\":\"audit.failure.2\",\"email\":\"audit.failure@summer.local\",\"displayName\":\"duplicate\"}")
                        .with(customerWriter()))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-events")
                        .queryParam("requestId", "req-failure")
                        .with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].result").value("FAILURE"))
                .andExpect(jsonPath("$.data.items[0].detail.reason").value("operation_failed"))
                .andExpect(jsonPath("$.data.items[0].detail.password").doesNotExist());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customerWriter() {
        return jwt().jwt(token -> token.subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_customer.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.subject("auditor"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }
}
