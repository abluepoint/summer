package com.abluepoint.summer.sample.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class IdentityHttpSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties properties;
    @Autowired protected org.springframework.jdbc.core.JdbcTemplate identityDb;
    @Autowired protected org.springframework.beans.factory.config.AutowireCapableBeanFactory identityBeans;
    @Autowired protected org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository identityClients;
    private com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture identityCurrent;
    private static final String BOOTSTRAP_SCOPES = "openid profile enterprise.roles.read enterprise.roles.write "
            + "enterprise.members.read enterprise.members.write enterprise.organization.read enterprise.organization.write "
            + "platform.capabilities.read platform.capabilities.write platform.enterprise.read platform.enterprise.write "
            + "platform.interface-resources.read enterprise.audit.read";

    protected com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture identityFacts() throws Exception {
        if (identityCurrent == null) {
            com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.initializeSchema(identityDb);
            identityCurrent = identityBeans.createBean(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class);
            identityCurrent.install(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform());
            identityCurrent.install(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"));
            // This explicit HTTP fixture uses the real public client and PKCE exchange.
            // Delegation scopes permit requests; current persisted resources still authorize them.
            var client = java.util.Objects.requireNonNull(identityClients.findByClientId("summer-ui"),
                    "The active public identity test client must be installed");
            identityClients.save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient.from(client)
                    .scopes(values -> values.addAll(java.util.Arrays.asList(BOOTSTRAP_SCOPES.split(" ")))).build());
        }
        return identityCurrent;
    }

    protected String bootstrapLogin() throws Exception {
        identityFacts();
        return login(properties.getAuthorizationServer().getUser().getUsername(), properties.getAuthorizationServer().getUser().getPassword(), BOOTSTRAP_SCOPES);
    }

    /** Explicit current test grants; input names select operations, never become Token business claims. */
    protected String authorizeSelf(String token, List<String> operations) throws Exception {
        var current = identityFacts();
        String username = properties.getAuthorizationServer().getUser().getUsername();
        for (var domain : List.of(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"),
                com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform())) {
            boolean enterprise = domain.kind().equals("ENTERPRISE");
            boolean requested = operations.stream().anyMatch(value -> enterprise ? value.startsWith("enterprise.")
                    : value.startsWith("platform.") || value.equals("POST:/api/users"));
            var roleIds = new java.util.ArrayList<String>();
            String prefix = enterprise ? "/api/v1/enterprises/ent-acme" : "/api/v1/platform";
            if (requested) {
                String permission = current.grantHandlers(domain, username, (handler, path, method) -> {
                    String owner = handler.getBeanType().getName();
                    if (owner.endsWith(".RolePermissionController"))
                        return enterprise && operations.stream().anyMatch(value -> value.startsWith("enterprise.roles."));
                    if (owner.endsWith(".AuthUserController"))
                        return !enterprise && operations.contains("POST:/api/users") && path.equals("/api/users") && method.equals("POST");
                    if (owner.endsWith(".InterfaceResourceCatalogController"))
                        return !enterprise && operations.contains("platform.interface-resources.read")
                                && method.equals("GET") && path.equals("/api/v1/platform/interface-resources");
                    var pre = handler.getMethodAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
                    return pre != null && operations.stream().anyMatch(value -> pre.value().contains("'SCOPE_" + value + "'"));
                });
                var created = mvc.perform(post(prefix + "/roles").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("name", "Regression management",
                                "code", "regression-" + UUID.randomUUID(), "permissionIds", List.of(permission)))))
                        .andExpect(status().isCreated()).andReturn();
                roleIds.add(JsonPath.read(created.getResponse().getContentAsString(), "$.data.roleId"));
            }
            var binding = mvc.perform(get(prefix + "/users/{username}/roles", username).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            mvc.perform(put(prefix + "/users/{username}/roles", username).header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", binding.getResponse().getHeader("ETag"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("roleIds", roleIds))))
                    .andExpect(status().isOk());
        }
        return bootstrapLogin();
    }
    protected String login(String username, String password) throws Exception {
        return login(username, password, "openid profile");
    }

    protected String login(String username, String password, String scopes) throws Exception {
        String verifier = UUID.randomUUID() + "-" + UUID.randomUUID();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", scopes).queryParam("state", UUID.randomUUID())
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        var initial = mvc.perform(get(uri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().is3xxRedirection());
        var callback = mvc.perform(get(uri).session(session).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String code = UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build().getQueryParams().getFirst("code");
        assertThat(code != null).isTrue();
        var issued = mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code")
                        .param("client_id", "summer-ui").param("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                        .param("code", code).param("code_verifier", verifier))
                .andExpect(status().isOk()).andReturn();
        String grantedScopes = JsonPath.read(issued.getResponse().getContentAsString(), "$.scope");
        assertThat(java.util.Arrays.asList(grantedScopes.split(" ")))
                .containsExactlyInAnyOrderElementsOf(java.util.Arrays.asList(scopes.split(" ")));
        session.invalidate();
        return JsonPath.read(issued.getResponse().getContentAsString(), "$.access_token");
    }
}
