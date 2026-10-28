package com.abluepoint.summer.sample.release;


import com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP permissions are configured through the same public APIs used by operators. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReleasePermissionRevocationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired SummerSecurityProperties properties;
    private final tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
    private static final String BASE = "/api/v1/enterprises/ent-acme";

    @Test void oldManagedTokenLosesReadAndWriteAfterReleaseOperatorIsRevoked() throws Exception {
        var ids = new java.util.ArrayList<String>();
        for (String code : List.of("internal.release.read", "internal.release.write")) {
            var response = mvc.perform(post(BASE + "/capabilities").with(manager("enterprise.capabilities.write"))
                    .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID())
                    .content(mapper.writeValueAsString(Map.of("code", code, "resource", code, "action", "invoke", "scope", "PLATFORM"))))
                    .andExpect(status().isCreated()).andReturn();
            ids.add(JsonPath.read(response.getResponse().getContentAsString(), "$.data.capabilityId"));
        }
        var created = mvc.perform(post(BASE + "/roles").with(manager("enterprise.roles.write"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Release operator", "code", "release-operator", "capabilityIds", ids))))
                .andExpect(status().isCreated()).andReturn();
        String role = JsonPath.read(created.getResponse().getContentAsString(), "$.data.roleId");
        String subject = properties.getAuthorizationServer().getUser().getUsername();
        String binding = BASE + "/users/" + subject + "/roles";
        String etag = bind(binding, role, List.of(), "\"1\"");
        var oldToken = jwt().jwt(token -> token.subject(subject).claim("summer_managed_grants", true)
                        .claim("summer_identity_source", "local-user").claim("release_operator", true)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"), new SimpleGrantedAuthority("SCOPE_internal.release.write"));
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken)).andExpect(status().isOk());
        etag = bind(binding, role, List.of("internal.release.read"), etag);
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken).header("X-Request-Id", "release-revoked"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("release-revoked"));
        mvc.perform(post("/internal/v1/release/gates:validate").with(oldToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"candidate\",\"dryRun\":true}"))
                .andExpect(status().isForbidden());
        bind(binding, role, List.of(), etag);
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken)).andExpect(status().isOk());
    }

    private String bind(String path, String role, List<String> denies, String etag) throws Exception {
        return mvc.perform(put(path).with(manager("enterprise.roles.write"))
                        .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roleIds", List.of(role), "deniedCapabilityCodes", denies))))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    }

    private static RequestPostProcessor manager(String permission) {
        return jwt().jwt(token -> token.subject("release-security-manager")
                        .claim("platform_capabilities", List.of("platform.capabilities.write"))
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(permission))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + permission));
    }
}
