package com.abluepoint.summer.sample.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "summer.sample.session-index.enabled=true",
        "summer.security.storage.session=redis",
        "spring.session.store-type=redis",
        "spring.data.redis.host=${SUMMER_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${SUMMER_REDIS_PORT:6379}",
        "spring.data.redis.username=${SUMMER_REDIS_USERNAME:default}",
        "spring.data.redis.password=${SUMMER_REDIS_PASSWORD:}",
        "spring.data.redis.database=15",
        "summer.sample.session-index.timeout-ms=2000",
        "summer.sample.session-index.retries=1",
        "summer.sample.session-index.failure-threshold=3",
        "summer.sample.session-index.open-duration-ms=1000"
})
class SharedSessionHttpTest {

    private static final String TEST_NAMESPACE = "summer:test:shared-session:" + java.util.UUID.randomUUID();

    @org.springframework.test.context.DynamicPropertySource
    static void redisNamespace(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.session.data.redis.namespace", () -> TEST_NAMESPACE + ":servlet");
        registry.add("summer.sample.session-index.namespace", () -> TEST_NAMESPACE + ":index");
    }

    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired SessionIndexService sessionIndex;
    MockMvc mockMvc;
    @Autowired org.springframework.web.context.WebApplicationContext application;

    @org.junit.jupiter.api.BeforeEach
    void securityChain() {
        // These five index/controller contracts use an existing servlet session.
        // Real Spring Session filter + cross-container cookies are exercised by SharedSessionNodesHttpTest.
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(application)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @org.junit.jupiter.api.AfterEach
    void removeTestSessions() {
        java.util.Set<String> keys = redis.keys(TEST_NAMESPACE + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void indexesTheCurrentSessionWithoutExposingTheServletSessionId() throws Exception {
        MockHttpSession session = authenticatedSession("operator",
                "SCOPE_platform.capabilities.read", "SCOPE_platform.capabilities.write");

        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/sessions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.items[0].sessionRef", not(is(session.getId()))))
                .andExpect(jsonPath("$.data.items[0].sessionRef").isString())
                .andExpect(jsonPath("$.data.items[0].principal").value("operator"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(session.getId());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("local-console-session");
    }

    @Test
    void revokingAListedSessionMakesTheSessionEndpointRejectIt() throws Exception {
        MockHttpSession session = authenticatedSession("operator",
                "SCOPE_platform.capabilities.read", "SCOPE_platform.capabilities.write");
        String originalSessionId = session.getId();
        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").session(session))
                .andExpect(status().isOk()).andReturn();
        String reference = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].sessionRef");
        org.assertj.core.api.Assertions.assertThat(reference).isEqualTo(SessionIndexService.reference(session.getId()));
        mockMvc.perform(delete("/api/v1/sessions/{reference}", reference).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        org.assertj.core.api.Assertions.assertThat(session.getId()).isEqualTo(originalSessionId);
        org.assertj.core.api.Assertions.assertThat(sessionIndex.load(originalSessionId).status()).isEqualTo("REVOKED");
        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
    }

    @Test
    void rejectsARevokeFromAUserWithoutSessionWriteCapability() throws Exception {
        MockHttpSession session = authenticatedSession("reader", "SCOPE_platform.capabilities.read");
        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").session(session))
                .andExpect(status().isOk()).andReturn();
        String reference = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].sessionRef");

        mockMvc.perform(delete("/api/v1/sessions/{reference}", reference).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void preventsAWriterFromRevokingAnotherPrincipalsSession() throws Exception {
        MockHttpSession ownerSession = authenticatedSession("owner",
                "SCOPE_platform.capabilities.read", "SCOPE_platform.capabilities.write");
        mockMvc.perform(get("/api/v1/session").session(ownerSession)).andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").session(ownerSession)).andExpect(status().isOk()).andReturn();
        String reference = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].sessionRef");

        var other = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("other"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"), new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.write"));
        mockMvc.perform(delete("/api/v1/sessions/{reference}", reference).with(other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void rejectsMalformedSessionReferencesBeforeRedisAccess() throws Exception {
        var operator = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("operator")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"), new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.write"));
        mockMvc.perform(delete("/api/v1/sessions/not-a-session-reference").with(operator))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_SESSION_REFERENCE_INVALID"))
                .andExpect(jsonPath("$.error.message").value(containsString("session reference")));
    }

    private MockHttpSession authenticatedSession(String principal, String... authorities) {
        MockHttpSession session = new MockHttpSession(null, java.util.UUID.randomUUID().toString());
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-session-context")
                .header("alg", "none").subject(principal).build();
        var authentication = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt, java.util.Arrays.stream(authorities)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        // Model an existing servlet login, without injecting a fresh authentication on every request.
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);
        sessionIndex.ensureCurrent(request, authentication);
        return session;
    }
}
