package com.abluepoint.summer.sample.authorization;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationChangeEventHttpTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void authorizationMutationIsTraceableAndCanBeRetried() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Event reader\",\"code\":\"event-reader\",\"capabilityIds\":[\"GET:/api/customers\"]}")
                        .header("Idempotency-Key", "event-role-1")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isCreated());

        String events = mockMvc.perform(get("/api/authorization-events")
                        .with(admin("enterprise.roles.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(events, "$.data.items[0].eventId");
        org.junit.jupiter.api.Assertions.assertEquals("ROLE_CREATED", JsonPath.read(events, "$.data.items[0].eventType"));

        mockMvc.perform(post("/api/authorization-events/" + eventId + "/retry")
                        .with(admin("enterprise.roles.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("security-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
