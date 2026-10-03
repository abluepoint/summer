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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsOnlyEnterpriseCapabilitiesAndDoesNotTreatOAuthScopeAsCapability() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capabilities/catalog")
                        .with(admin("enterprise.capabilities.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspace").value("enterprise"))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.capabilities[*].scope").value(everyItem(is("ENTERPRISE"))))
                .andExpect(jsonPath("$.data.capabilities[?(@.code == 'enterprise.members.read')].riskLevel").value("LOW"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capabilities/catalog")
                        .with(jwt().jwt(token -> token.subject("alice")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.capabilities.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void separatesPlatformCapabilityCatalogFromEnterpriseWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/platform/capabilities/catalog")
                        .with(platformAdmin("platform.capabilities.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspace").value("platform"))
                .andExpect(jsonPath("$.data.enterpriseId").doesNotExist())
                .andExpect(jsonPath("$.data.capabilities[*].scope").value(everyItem(is("PLATFORM"))));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capabilities/catalog")
                        .with(admin("enterprise.capabilities.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities[*].scope").value(everyItem(is("ENTERPRISE"))));
    }

    @Test
    void disabledCapabilityCannotBeBoundAndRoleCapabilityHasNoEffectField() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Auditor\",\"code\":\"auditor\",\"capabilityIds\":[\"cap-enterprise-members-read\"]}")
                        .header("Idempotency-Key", "role-create-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roleId").isNotEmpty())
                .andExpect(jsonPath("$.data.capabilityIds[0]").value("cap-enterprise-members-read"))
                .andExpect(jsonPath("$.data.effect").doesNotExist())
                .andReturn();
        String roleId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.roleId");
        String etag = created.getResponse().getHeader("ETag");

        mockMvc.perform(put("/api/v1/enterprises/ent-acme/roles/" + roleId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilityIds\":[\"cap-enterprise-members-read\",\"cap-enterprise-members-write\"]}")
                        .header("If-Match", etag).header("Idempotency-Key", "role-capabilities-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/capabilities/cap-enterprise-members-write:disable")
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "cap-disable-1")
                        .with(admin("enterprise.capabilities.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(put("/api/v1/enterprises/ent-acme/roles/" + roleId + "/capabilities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilityIds\":[\"cap-enterprise-members-write\"]}")
                        .header("If-Match", "\"2\"").header("Idempotency-Key", "role-capabilities-disabled")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idempotentRoleWriteReplaysAndStaleEtagIsRejected() throws Exception {
        String body = "{\"name\":\"Operator\",\"code\":\"operator\"}";
        MvcResult first = mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "role-replay-1").with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated()).andReturn();
        String firstEtag = first.getResponse().getHeader("ETag");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "role-replay-1").with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", firstEtag));
        String roleId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.roleId");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/roles/" + roleId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}")
                        .header("If-Match", "\"999\"").header("Idempotency-Key", "role-stale-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
    }

    @Test
    void bundleUpdatesKeepVersionHistoryAndExpandIntoRole() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/capability-bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Helpdesk\",\"code\":\"helpdesk\",\"capabilityIds\":[\"cap-enterprise-members-read\"],\"changeSummary\":\"initial\"}")
                        .header("Idempotency-Key", "bundle-create-1").with(admin("enterprise.bundles.write")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(1)).andReturn();
        String bundleId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.bundleId");
        String etag = created.getResponse().getHeader("ETag");
        MvcResult updated = mockMvc.perform(patch("/api/v1/enterprises/ent-acme/capability-bundles/" + bundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilityIds\":[\"cap-enterprise-members-read\",\"cap-enterprise-members-write\"],\"changeSummary\":\"add write\"}")
                        .header("If-Match", etag).header("Idempotency-Key", "bundle-update-1")
                        .with(admin("enterprise.bundles.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2)).andReturn();
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capability-bundles/" + bundleId + "/versions")
                        .with(admin("enterprise.bundles.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Helpdesk role\",\"code\":\"helpdesk-role\",\"bundleId\":\"" + bundleId + "\"}")
                        .header("Idempotency-Key", "role-bundle-1").with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capabilityIds", containsInAnyOrder("cap-enterprise-members-read", "cap-enterprise-members-write")));
        String roleId = JsonPath.read(mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Helpdesk role\",\"code\":\"helpdesk-role-2\",\"bundleId\":\"" + bundleId + "\"}")
                        .header("Idempotency-Key", "role-bundle-2").with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.roleId");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/roles/" + roleId)
                        .with(admin("enterprise.roles.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.capabilityIds", containsInAnyOrder("cap-enterprise-members-read", "cap-enterprise-members-write")));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capability-bundles/" + bundleId)
                        .with(admin("enterprise.bundles.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changeSummary").value("add write"));
    }

    @Test
    void rejectsCrossEnterpriseAndInactiveMembership() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-other/roles").with(admin("enterprise.roles.read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/roles")
                        .with(jwt().jwt(token -> token.subject("disabled")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "DISABLED")))
                                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.roles.read"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("security-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin(String capability) {
        return jwt().jwt(token -> token.subject("platform-operator")
                        .claim("platform_capabilities", List.of(capability)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
