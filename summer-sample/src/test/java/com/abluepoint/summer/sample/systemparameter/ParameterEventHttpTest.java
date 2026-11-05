package com.abluepoint.summer.sample.systemparameter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class ParameterEventHttpTest {
    // These legacy fixtures now declare their enterprise; production has no implicit sample tenant.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }

    @Autowired MockMvc http;

    @Test
    void committedParameterChangeHasQueryableValueFreeDelivery() throws Exception {
        http.perform(post("/api/system-parameters")
                        .with(jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "system.parameter.write")).authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write")))
                        .header("X-Request-Id", "event-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"events.page-size","type":"INTEGER","value":25}
                                """))
                .andExpect(status().isCreated());
        var events = http.perform(get("/api/system-parameter-events")
                        .param("key", "events.page-size")
                        .with(jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "system.parameter.read")).authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].event.eventType").value("SYSTEM_PARAMETER_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].event.parameterKey").value("events.page-size"))
                .andExpect(jsonPath("$.data.items[0].event.version").value(0))
                .andExpect(jsonPath("$.data.items[0].event.requestId").value("event-create"))
                .andExpect(jsonPath("$.data.items[0].event.value").doesNotExist())
                .andReturn();
        String eventId = JsonPath.read(events.getResponse().getContentAsString(), "$.data.items[0].eventId");

        http.perform(post("/api/system-parameter-events/{eventId}/retry", eventId)
                        .with(jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "system.parameter.write")).authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        http.perform(get("/api/system-parameter-events")
                        .with(jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "unknown")).authorities(new SimpleGrantedAuthority("SCOPE_unknown"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }
}
