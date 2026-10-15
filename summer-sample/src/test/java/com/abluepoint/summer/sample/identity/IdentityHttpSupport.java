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
    protected String bootstrapLogin() throws Exception {
        return login(properties.getAuthorizationServer().getUser().getUsername(), properties.getAuthorizationServer().getUser().getPassword());
    }
    protected String authorizeSelf(String token, List<String> capabilities) throws Exception {
        String username = properties.getAuthorizationServer().getUser().getUsername();
        var mapper = new tools.jackson.databind.ObjectMapper();
        var created = mvc.perform(post("/api/v1/enterprises/ent-acme/roles").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", "Regression management", "code", "regression-" + UUID.randomUUID(), "capabilityIds", capabilities))))
                .andExpect(status().isCreated()).andReturn();
        String role = JsonPath.read(created.getResponse().getContentAsString(), "$.data.roleId");
        mvc.perform(put("/api/v1/enterprises/ent-acme/users/{username}/roles", username).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID()).header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("roleIds", List.of(role), "deniedCapabilityCodes", List.of()))))
                .andExpect(status().isOk());
        return bootstrapLogin();
    }
    protected String login(String username, String password) throws Exception {
        String verifier = UUID.randomUUID() + "-" + UUID.randomUUID();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile").queryParam("state", UUID.randomUUID())
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
        session.invalidate();
        return JsonPath.read(issued.getResponse().getContentAsString(), "$.access_token");
    }
}
