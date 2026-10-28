package com.abluepoint.summer.sample.identity.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SessionControllerTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsAProtocolSafeSessionSnapshotWithAnEtag() throws Exception {
        String token = accessToken("customer.read");

        MvcResult first = mockMvc.perform(get("/api/v1/session")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "session-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "session-request"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.principal.subject").value("summer-client"))
                .andExpect(jsonPath("$.data.productSession.expiresAt").isString())
                .andExpect(jsonPath("$.data.workspaces[0].code").value("platform-operations"))
                .andExpect(jsonPath("$.data.enterpriseMemberships").isArray())
                .andExpect(jsonPath("$.data.access_token").doesNotExist())
                .andExpect(jsonPath("$.data.refresh_token").doesNotExist())
                .andReturn();

        String etag = first.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/v1/session")
                        .header("Authorization", "Bearer " + token)
                .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    @Test
    void unassignedLocalUserCanReadOnlyTheirOwnSessionWithoutMembership() throws Exception {
        mockMvc.perform(get("/api/v1/session")
                        .with(jwt().jwt(token -> token.subject("unassigned-user").claim("summer_identity_source", "local-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.principal.subject").value("unassigned-user"))
                .andExpect(jsonPath("$.data.enterpriseMemberships").isEmpty());
        mockMvc.perform(get("/api/v1/sessions")
                        .with(jwt().jwt(token -> token.subject("unassigned-user").claim("summer_identity_source", "local-user"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/sessions/another-session")
                        .with(jwt().jwt(token -> token.subject("unassigned-user").claim("summer_identity_source", "local-user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassignedLocalUserCanLogOutWithoutMembership() throws Exception {
        mockMvc.perform(delete("/api/v1/session")
                        .with(jwt().jwt(token -> token.subject("unassigned-user").claim("summer_identity_source", "local-user"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void requiresAuthenticationToReadSessionSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
    }

    @Test
    void logoutIsIdempotentForAuthenticatedAndExpiredSessions() throws Exception {
        mockMvc.perform(delete("/api/v1/session"))
                .andExpect(status().isNoContent());

        String token = accessToken("customer.read");
        mockMvc.perform(delete("/api/v1/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private String accessToken(String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }
}
