package com.abluepoint.summer.sample;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    @Autowired
    OAuth2AuthorizationService authorizationService;

    @Autowired
    RegisteredClientRepository clients;

    @Value("${summer.security.authorization-server.user.password}")
    String loginPassword;

    @Test
    void keepsTwoIndependentBrowserLoginSessionsAuthenticated() throws Exception {
        java.net.URI authorizationUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile")
                .queryParam("state", "parallel-session")
                .queryParam("nonce", "parallel-session")
                .queryParam("code_challenge", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUri();

        MockHttpSession first = beginAndLogin(authorizationUri);
        MockHttpSession second = beginAndLogin(authorizationUri);
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.isInvalid()).isFalse();
        assertThat(second.isInvalid()).isFalse();

        for (MockHttpSession session : new MockHttpSession[] {first, second}) {
            mockMvc.perform(get(authorizationUri).session(session).header("Accept", "text/html"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                            "http://127.0.0.1:5173/oauth2/callback?code=")));
        }
    }

    private MockHttpSession beginAndLogin(java.net.URI authorizationUri) throws Exception {
        MvcResult authorization = mockMvc.perform(get(authorizationUri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user")
                        .param("password", loginPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    @Test
    void registersPublicSummerUiPolicy() {
        RegisteredClient client = clients.findByClientId("summer-ui");
        assertThat(client).isNotNull();
        assertThat(client.getClientSecret()).isNull();
        assertThat(client.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "profile", "customer.read", "customer.write");
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(client.getTokenSettings().getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void productionSampleConfigHasNoLegacyClient() throws Exception {
        var source = new YamlPropertySourceLoader().load("sample",
                new FileSystemResource("src/main/resources/application.yml")).getFirst();
        assertThat(source.getProperty("summer.security.authorization-server.clients[0].id").toString())
                .contains("summer-ui");
        assertThat(source.getProperty("summer.security.authorization-server.clients[1].id")).isNull();
    }

    @Test
    void rejectsPlainPkceForSummerUi() throws Exception {
        java.net.URI uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid")
                .queryParam("state", "plain-state")
                .queryParam("code_challenge", "summer-plain-challenge-012345678901234567890123")
                .queryParam("code_challenge_method", "plain")
                .build().encode().toUri();
        MvcResult csrfResponse = mockMvc.perform(get("/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResponse.getRequest().getSession(false);
        String csrfBody = csrfResponse.getResponse().getContentAsString();
        String csrfParameter = JsonPath.read(csrfBody, "$.parameterName");
        String csrfToken = JsonPath.read(csrfBody, "$.token");
        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user").param("password", loginPassword)
                        .param(csrfParameter, csrfToken))
                .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        MvcResult result = mockMvc.perform(get(uri).session(session))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location == null || !location.contains("code=")).isTrue();
        java.net.URI missingChallenge = UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam("code_challenge")
                .replaceQueryParam("code_challenge_method")
                .build().encode().toUri();
        MvcResult missing = mockMvc.perform(get(missingChallenge).session(session)).andReturn();
        String missingLocation = missing.getResponse().getHeader("Location");
        assertThat(missingLocation == null || !missingLocation.contains("code=")).isTrue();
    }

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
                        .param("password", loginPassword)
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
        var issuedCode = authorizationService.findByToken(code, new OAuth2TokenType("code"));
        assertThat(issuedCode).isNotNull();
        var codeToken = issuedCode.getToken(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class);
        assertThat(codeToken).isNotNull();
        assertThat(Duration.between(codeToken.getToken().getIssuedAt(), codeToken.getToken().getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(5));

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
        assertThat(jwtPayload(idToken).keySet()).doesNotContain("email", "role", "roles", "authority",
                "authorities", "customer", "customerId", "scope", "scp", "permissions", "groups");
        assertThat(JsonPath.<String>read(body, "$.scope").split(" "))
                .containsExactlyInAnyOrder("openid", "profile", "customer.read", "customer.write");
        long expiresAt = ((Number) jwtPayload(idToken).get("exp")).longValue();
        assertThat(expiresAt).isBetween(Instant.now().plusSeconds(14 * 60).getEpochSecond(),
                Instant.now().plusSeconds(16 * 60).getEpochSecond());
        long accessExpiresAt = ((Number) jwtPayload(accessToken).get("exp")).longValue();
        assertThat(accessExpiresAt).isBetween(Instant.now().plusSeconds(14 * 60).getEpochSecond(),
                Instant.now().plusSeconds(16 * 60).getEpochSecond());

        session.invalidate();
        mockMvc.perform(get(authorizationUri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void exposesCsrfAsRawProtocolJson() throws Exception {
        mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.token").isString())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").doesNotExist());
    }

    @Test
    void refusesClientCredentialsAndRefreshGrantWithoutBusinessEnvelope() throws Exception {
        for (String grant : new String[] {"client_credentials", "refresh_token", "password"}) {
            MvcResult result = mockMvc.perform(post("/oauth2/token")
                            .param("client_id", "summer-ui")
                            .param("grant_type", grant))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("\"success\"");
        }
    }

    @Test
    void loginRejectsMissingAndWrongCsrfThenRestoresSavedAuthorizeRequest() throws Exception {
        String verifier = "summer-login-verifier-012345678901234567890123456";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        java.net.URI authorizationUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile customer.read customer.write")
                .queryParam("state", "login-state")
                .queryParam("nonce", "login-nonce")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        MvcResult authorize = mockMvc.perform(get(authorizationUri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) authorize.getRequest().getSession(false);
        MvcResult csrfResponse = mockMvc.perform(get("/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        String csrfBody = csrfResponse.getResponse().getContentAsString();
        String csrfParameter = JsonPath.read(csrfBody, "$.parameterName");
        String csrfToken = JsonPath.read(csrfBody, "$.token");

        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user").param("password", loginPassword))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user").param("password", loginPassword)
                        .param(csrfParameter, "incorrect"))
                .andExpect(status().isForbidden());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        String retryCsrfBody = mockMvc.perform(get("/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        csrfToken = JsonPath.read(retryCsrfBody, "$.token");

        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user").param("password", loginPassword + "-wrong")
                        .param(csrfParameter, csrfToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?error"));
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();

        MvcResult login = mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user").param("password", loginPassword)
                        .param(csrfParameter, csrfToken))
                .andExpect(status().is3xxRedirection()).andReturn();
        String savedLocation = login.getResponse().getHeader("Location");
        assertThat(savedLocation).contains("/oauth2/authorize");
        MvcResult callback = mockMvc.perform(get(java.net.URI.create(savedLocation)).session(session)
                        .header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String callbackLocation = callback.getResponse().getHeader("Location");
        assertThat(callbackLocation != null && callbackLocation.contains("code=")).isTrue();
    }

    @Test
    void publishesEndSessionDiscoveryAndClearsLoginSessionOnRpInitiatedLogout() throws Exception {
        String discovery = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(discovery, "$.end_session_endpoint"))
                .isEqualTo("http://127.0.0.1:8080/connect/logout");

        String verifier = "summer-pkce-verifier-logout-012345678901234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        java.net.URI authorizationUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile customer.read customer.write")
                .queryParam("state", "logout-state")
                .queryParam("nonce", "logout-nonce")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        MvcResult authorization = mockMvc.perform(get(authorizationUri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        String sessionIdBeforeLogin = session.getId();
        mockMvc.perform(post("/login").session(session)
                        .param("username", "summer-user")
                        .param("password", loginPassword).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);

        MvcResult callback = mockMvc.perform(get(authorizationUri).session(session)
                        .header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String code = UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build().getQueryParams().getFirst("code");
        String tokenBody = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", "summer-ui")
                        .param("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                        .param("code", code).param("code_verifier", verifier))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(tokenBody, "$.access_token");
        String idToken = JsonPath.read(tokenBody, "$.id_token");
        assertThat(authorizationService.findByToken(idToken, new OAuth2TokenType("id_token"))).isNotNull();
        mockMvc.perform(get(java.net.URI.create("/connect/logout?id_token_hint=invalid"))
                        .session(session))
                .andExpect(status().isBadRequest());
        assertThat(session.isInvalid()).isFalse();
        java.net.URI logoutUri = UriComponentsBuilder.fromPath("/connect/logout")
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", "http://127.0.0.1:5173/login?logout")
                .queryParam("state", "logout-state")
                .build().encode().toUri();
        MvcResult logout = mockMvc.perform(get(logoutUri).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://127.0.0.1:5173/login?logout&state=logout-state"))
                .andReturn();
        assertThat(session.isInvalid()).isTrue();
        assertThat(logout.getResponse().getCookies())
                .anyMatch(cookie -> "JSESSIONID".equals(cookie.getName()) && cookie.getMaxAge() == 0);
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private static java.util.Map<String, Object> jwtPayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return new tools.jackson.databind.ObjectMapper().readValue(new String(decoded, StandardCharsets.UTF_8), java.util.Map.class);
    }
}
