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
        "summer.sample.session-index.timeout-ms=100ms",
        "summer.sample.session-index.retries=1",
        "summer.sample.session-index.failure-threshold=3",
        "summer.sample.session-index.open-duration-ms=1s"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SharedSessionHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void indexesTheCurrentSessionWithoutExposingTheServletSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var operator = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("operator")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"), new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.write"));

        mockMvc.perform(get("/api/v1/session").with(operator).session(session))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/v1/sessions").with(operator).session(session))
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
        MockHttpSession session = new MockHttpSession();
        var operator = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("operator")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"), new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.write"));
        mockMvc.perform(get("/api/v1/session").with(operator).session(session))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").with(operator).session(session))
                .andExpect(status().isOk()).andReturn();
        String reference = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].sessionRef");

        mockMvc.perform(delete("/api/v1/sessions/{reference}", reference).with(operator).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/session").with(operator).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_SESSION_REVOKED"));
    }

    @Test
    void rejectsARevokeFromAUserWithoutSessionWriteCapability() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var reader = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("reader")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));
        mockMvc.perform(get("/api/v1/session").with(reader).session(session))
                .andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").with(reader).session(session))
                .andExpect(status().isOk()).andReturn();
        String reference = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].sessionRef");

        mockMvc.perform(delete("/api/v1/sessions/{reference}", reference).with(reader).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void preventsAWriterFromRevokingAnotherPrincipalsSession() throws Exception {
        MockHttpSession ownerSession = new MockHttpSession();
        var owner = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("owner"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"), new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.write"));
        mockMvc.perform(get("/api/v1/session").with(owner).session(ownerSession)).andExpect(status().isOk());
        MvcResult listed = mockMvc.perform(get("/api/v1/sessions").with(owner).session(ownerSession)).andExpect(status().isOk()).andReturn();
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
}
