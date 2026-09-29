package com.abluepoint.summer.sample;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PublicClientPkceIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void completesAuthorizationCodePkceAndKeepsProtocolResponsesRaw() throws Exception {
        String verifier = "summer-pkce-verifier-012345678901234567890123456789";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        java.net.URI authorizationUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile customer.read customer.write")
                .queryParam("state", "state-1")
                .queryParam("nonce", "nonce-1")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        MvcResult authorization = mockMvc.perform(get(authorizationUri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", "summer-user")
                        .param("password", "summer-user-demo-only")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        MvcResult callback = mockMvc.perform(get(authorizationUri)
                        .session(session)
                        .header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = callback.getResponse().getHeader("Location");
        assertThat(location).contains("http://127.0.0.1:5173/oauth2/callback");
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        assertThat(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state"))
                .isEqualTo("state-1");

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "summer-ui")
                        .param("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                        .param("code", code)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();
        String body = token.getResponse().getContentAsString();
        assertThat(body).doesNotContain("refresh_token").doesNotContain("success");
        String accessToken = JsonPath.read(body, "$.access_token");
        String idToken = JsonPath.read(body, "$.id_token");
        assertThat(accessToken).isNotBlank();
        assertThat(idToken).isNotBlank();
        assertThat(jwtPayload(idToken).get("sub")).isEqualTo("summer-user");
        assertThat(jwtPayload(idToken).get("preferred_username")).isEqualTo("summer-user");
        assertThat(jwtPayload(idToken).get("name")).isEqualTo("summer-user");
        long expiresAt = ((Number) jwtPayload(idToken).get("exp")).longValue();
        assertThat(expiresAt).isBetween(Instant.now().plusSeconds(14 * 60).getEpochSecond(),
                Instant.now().plusSeconds(16 * 60).getEpochSecond());
    }

    @Test
    void exposesCsrfAsRawProtocolJson() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.token").isString())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").doesNotExist());
    }

    private static java.util.Map<String, Object> jwtPayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return new tools.jackson.databind.ObjectMapper().readValue(new String(decoded, StandardCharsets.UTF_8), java.util.Map.class);
    }
}
