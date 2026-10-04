package com.abluepoint.summer.sample.authorization;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationAssignmentHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void roleBindingToScannedResourceAllowsUserAndAuditExplainsDecision() throws Exception {
        MvcResult role = mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Customer reader\",\"code\":\"customer-reader\",\"capabilityIds\":[\"GET:/api/customers\"]}")
                        .header("Idempotency-Key", "assignment-role-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = JsonPath.read(role.getResponse().getContentAsString(), "$.data.roleId");

        mockMvc.perform(put("/api/v1/enterprises/ent-acme/users/alice/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + roleId + "\"],\"deniedCapabilityCodes\":[]}")
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "assignment-user-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.roleIds[0]").value(roleId));

        mockMvc.perform(get("/api/customers")
                        .with(user("alice", "customer.read")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/authorization-audit/alice")
                        .with(admin("enterprise.roles.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.decision == 'ALLOW')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'GET:/api/customers')]").isNotEmpty());
    }

    @Test
    void explicitUserDenyWinsOverRoleAllowAndUnconfiguredResourceFailsClosed() throws Exception {
        MvcResult role = mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Customer reader\",\"code\":\"customer-reader\",\"capabilityIds\":[\"GET:/api/customers\"]}")
                        .header("Idempotency-Key", "assignment-role-2")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated())
                .andReturn();
        String roleId = JsonPath.read(role.getResponse().getContentAsString(), "$.data.roleId");

        mockMvc.perform(put("/api/v1/enterprises/ent-acme/users/bob/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + roleId + "\"],\"deniedCapabilityCodes\":[\"GET:/api/customers\"]}")
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "assignment-user-2")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deniedCapabilityCodes[0]").value("GET:/api/customers"));

        mockMvc.perform(get("/api/customers")
                        .with(user("bob", "customer.read")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/authorization-audit/bob")
                        .with(admin("enterprise.roles.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.decision == 'DENY')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.reasonCode == 'USER_EXPLICIT_DENY')]").isNotEmpty());
    }

    @Test
    void assignmentBoundaryRejectsUnauthenticatedAndStaleWrites() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));

        mockMvc.perform(put("/api/v1/enterprises/ent-acme/users/alice/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[],\"deniedCapabilityCodes\":[]}")
                        .header("If-Match", "\"99\"")
                        .header("Idempotency-Key", "assignment-invalid-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("security-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String username, String scope) {
        return jwt().jwt(token -> token.subject(username)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
