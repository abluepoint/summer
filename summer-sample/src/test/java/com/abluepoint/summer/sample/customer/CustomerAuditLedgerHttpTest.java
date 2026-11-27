package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class CustomerAuditLedgerHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        current.grantHandlers(Domain.platform(), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                        && java.util.Set.of(
                                "POST /api/customers",
                                "PATCH /api/customers/{username}",
                                "DELETE /api/customers/{username}")
                        .contains(method + " " + path));
        current.grantHandlers(Domain.platform(), "auditor", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerAuditController")
                        && java.util.Set.of(
                                "GET /api/audit-events",
                                "GET /api/audit-events/export")
                        .contains(method + " " + path));
    }


    @Autowired
    MockMvc mockMvc;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    @Autowired AuditOrganizationContext organizationContext;

    @Test
    void auditContextRejectsRevokedOrForeignOrganizationSelections() {
        members.provision("ent-acme", "audit-context-member", "audit-context-subject", "Audit member", "context@example.test");
        jdbc.update("insert into summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "ent-acme", "audit-context-member", "org-engineering", true);
        jdbc.update("insert into summer_organization_context(subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "audit-context-subject", "ent-acme", "org-engineering", "audit-context-boundary");
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "audit-context-subject", null, List.of());
        org.junit.jupiter.api.Assertions.assertEquals("org-engineering", organizationContext.current("ent-acme", authentication));
        org.junit.jupiter.api.Assertions.assertNull(organizationContext.current("ent-foreign", authentication));
        jdbc.update("update summer_enterprise_member set status='INACTIVE' where member_id=?", "audit-context-member");
        org.junit.jupiter.api.Assertions.assertNull(organizationContext.current("ent-acme", authentication));
        jdbc.update("update summer_enterprise_member set status='ACTIVE' where member_id=?", "audit-context-member");
        jdbc.update("delete from summer_organization_membership where member_id=?", "audit-context-member");
        org.junit.jupiter.api.Assertions.assertNull(organizationContext.current("ent-acme", authentication));
        org.junit.jupiter.api.Assertions.assertNull(organizationContext.current("ent-acme", null));
    }

    @Test
    void selectedPersistedOrganizationIsRecordedInsteadOfAnOldTokenClaim() throws Exception {
        members.provision("ent-acme", "audit-operator", "operator", "Audit operator", "audit-operator@example.test");
        jdbc.update("insert into summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "ent-acme","audit-operator","org-engineering",true);
        jdbc.update("insert into summer_organization_context(subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "operator","ent-acme","org-engineering","audit-context");
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id","audit-current-org")
                        .content("{\"username\":\"audit.org\",\"email\":\"audit.org@example.test\",\"displayName\":\"Context\"}")
                        .with(jwt().jwt(token->token.claim("scope", "customer.write").subject("operator").claim("organization_id","org-stale"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_customer.write"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit-events").queryParam("requestId","audit-current-org").with(auditReader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].organizationId").value("org-engineering"));
    }

    @Test
    void auditReadOnlyResourceRejectsUnsupportedMethodsWith405() throws Exception {
        for(String method:List.of("PATCH","DELETE")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            org.springframework.http.HttpMethod.valueOf(method),"/api/audit-events/immutable")
                            .contentType(MediaType.APPLICATION_JSON).content("{}").with(auditReader()))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow",org.hamcrest.Matchers.containsString("GET")))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("SUMMER_METHOD_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }

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
    void malformedTimeInSearchIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/audit-events").queryParam("from", "yesterday").with(auditReader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"));
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
        return jwt().jwt(token -> token.claim("scope", "customer.write").subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_customer.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auditReader() {
        return jwt().jwt(token -> token.claim("scope", "enterprise.audit.read").subject("auditor"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }
}
