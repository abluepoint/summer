package com.abluepoint.summer.sample.release;


import com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReleasePlatformScopeHttpTest {
    @Autowired MockMvc mvc;
    @Autowired SummerSecurityProperties properties;
    private final tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
    private static final String BASE = "/api/v1/enterprises/ent-acme";

    @ParameterizedTest
    @ValueSource(strings = {"internal.release.read", "internal.release.write"})
    void sameCodeEnterpriseGrantCannotReplaceRevokedPlatformGrant(String revoked) throws Exception {
        var platform = new java.util.LinkedHashMap<String, String>();
        for (String code : List.of("internal.release.read", "internal.release.write")) platform.put(code, capability(code, "PLATFORM"));
        String enterpriseAlias = capability(revoked, "ENTERPRISE");
        List<String> ids = new ArrayList<>(platform.values());
        ids.add(enterpriseAlias);
        var created = mvc.perform(post(BASE + "/roles").with(manager("enterprise.roles.write"))
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID())
                        .content(mapper.writeValueAsString(Map.of("name", "Release operator", "code", "scope-test", "capabilityIds", ids))))
                .andExpect(status().isCreated()).andReturn();
        String role = JsonPath.read(created.getResponse().getContentAsString(), "$.data.roleId");
        String subject = properties.getAuthorizationServer().getUser().getUsername();
        mvc.perform(put(BASE + "/users/" + subject + "/roles").with(manager("enterprise.roles.write"))
                        .header("If-Match", "\"1\"").header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roleIds", List.of(role), "deniedCapabilityCodes", List.of()))))
                .andExpect(status().isOk());
        var oldToken = jwt().jwt(token -> token.subject(subject).claim("summer_managed_grants", true)
                        .claim("summer_identity_source", "local-user").claim("release_operator", true)
                        .claim("platform_capabilities", List.of("internal.release.read", "internal.release.write"))
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"), new SimpleGrantedAuthority("SCOPE_internal.release.write"));
        releaseRequest(revoked, oldToken).andExpect(status().isOk());
        ids.remove(platform.get(revoked));
        mvc.perform(put(BASE + "/roles/" + role + "/capabilities").with(manager("enterprise.roles.write"))
                        .header("If-Match", created.getResponse().getHeader("ETag")).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("capabilityIds", ids))))
                .andExpect(status().isOk());
        releaseRequest(revoked, oldToken).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("release-scope-revocation"));
    }

    @org.junit.jupiter.api.Test
    void platformReadAndWriteCanComeFromDifferentActiveEnterprises() throws Exception {
        String subject = properties.getAuthorizationServer().getUser().getUsername();
        var enterprise = mvc.perform(post("/api/v1/platform/enterprises")
                        .with(manager("platform.enterprise.write", "ent-acme", subject)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Release second enterprise", "code", "release-" + UUID.randomUUID()))))
                .andExpect(status().isCreated()).andReturn();
        String second = JsonPath.read(enterprise.getResponse().getContentAsString(), "$.data.enterpriseId");
        String read = capability("internal.release.read", "PLATFORM");
        String write = capability("internal.release.write", "PLATFORM");
        for (String tenant : List.of("ent-acme", second)) {
            String base = "/api/v1/enterprises/" + tenant;
            var role = mvc.perform(post(base + "/roles").with(manager("enterprise.roles.write", tenant, "release-scope-manager"))
                            .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("name", "Split release grant", "code", "split-release", "capabilityIds", List.of(tenant.equals("ent-acme") ? read : write)))))
                    .andExpect(status().isCreated()).andReturn();
            String roleId = JsonPath.read(role.getResponse().getContentAsString(), "$.data.roleId");
            String etag = "\"1\"";
            if (tenant.equals(second)) etag = mvc.perform(get(base + "/users/" + subject + "/roles")
                            .with(manager("enterprise.roles.read", tenant, "release-scope-manager")))
                    .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
            mvc.perform(put(base + "/users/" + subject + "/roles").with(manager("enterprise.roles.write", tenant, "release-scope-manager"))
                            .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", etag).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("roleIds", List.of(roleId), "deniedCapabilityCodes", List.of()))))
                    .andExpect(status().isOk());
        }
        var oldToken = jwt().jwt(token -> token.subject(subject).claim("summer_managed_grants", true)
                        .claim("summer_identity_source", "local-user").claim("release_operator", true)
                        .claim("active_enterprise_id", "ent-acme")
                        .claim("platform_capabilities", List.of("internal.release.read", "internal.release.write"))
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"), Map.of("enterpriseId", second, "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"), new SimpleGrantedAuthority("SCOPE_internal.release.write"));
        releaseRequest("internal.release.write", oldToken).andExpect(status().isOk());
        String membersPath = "/api/v1/enterprises/" + second + "/members";
        var members = mvc.perform(get(membersPath).with(manager("enterprise.members.read", second, "release-scope-manager")))
                .andExpect(status().isOk()).andReturn();
        String memberId = JsonPath.read(members.getResponse().getContentAsString(), "$.data.items[0].memberId");
        var member = mvc.perform(get(membersPath + "/" + memberId).with(manager("enterprise.members.read", second, "release-scope-manager")))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post(membersPath + "/" + memberId + ":disable").with(manager("enterprise.members.write", second, "release-scope-manager"))
                        .header("If-Match", member.getResponse().getHeader("ETag")).header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk());
        releaseRequest("internal.release.write", oldToken).andExpect(status().isForbidden());
        releaseRequest("internal.release.read", oldToken).andExpect(status().isOk());
    }

    private String capability(String code, String scope) throws Exception {
        var result = mvc.perform(post(BASE + "/capabilities").with(manager("enterprise.capabilities.write"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", code, "resource", code, "action", "invoke", "scope", scope))))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.capabilityId");
    }

    private ResultActions releaseRequest(String permission, RequestPostProcessor token) throws Exception {
        if (permission.endsWith("read")) return mvc.perform(get("/internal/v1/release/readiness")
                .with(token).header("X-Request-Id", "release-scope-revocation"));
        return mvc.perform(post("/internal/v1/release/gates:validate").with(token)
                .header("X-Request-Id", "release-scope-revocation").header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"releaseVersion\":\"scope-test\",\"dryRun\":true}"));
    }

    private static RequestPostProcessor manager(String permission) {
        return manager(permission, "ent-acme", "release-scope-manager");
    }

    private static RequestPostProcessor manager(String permission, String enterprise, String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("principal_type", "USER")
                        .claim("platform_capabilities", List.of("platform.capabilities.write", permission))
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterprise, "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of(enterprise, List.of(permission))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + permission));
    }
}
