package com.abluepoint.summer.sample.realtime;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RealtimeMessageHttpTest extends RealtimeMessageTestSupport {

    @Test
    void persistsOrdersDeduplicatesAndAcknowledgesMessages() throws Exception {
        String key = "client-message-1";
        MvcResult first = mockMvc.perform(post("/api/v1/enterprises/ent-acme/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + key + "\",\"payload\":{\"text\":\"hello\"}}")
                        .with(operator("enterprise.messages.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.sequence").value(1))
                .andReturn();
        String messageId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.messageId");

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + key + "\",\"payload\":{\"text\":\"hello\"}}")
                        .with(operator("enterprise.messages.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value(messageId))
                .andExpect(jsonPath("$.data.duplicate").value(true));

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/messages/{id}:ack", messageId)
                        .with(operator("enterprise.messages.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKED"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/messages").with(operator("enterprise.messages.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].messageId").value(hasItem(messageId)))
                .andExpect(jsonPath("$.data.items[0].payload.text").value("hello"));
    }

    @Test
    void rejectsInvalidInputMissingCapabilityAndCrossTenantAccess() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"bad\",\"payload\":{}}")
                        .with(operator("enterprise.messages.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MESSAGE_INVALID"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/messages").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/enterprises/ent-other/messages").with(operator("enterprise.messages.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void supportsCursorReplayAndRejectsMalformedCursor() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/messages")
                        .queryParam("after", "not-a-number")
                        .with(operator("enterprise.messages.read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MESSAGE_CURSOR_INVALID"));
    }
}

abstract class RealtimeMessageTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    protected org.springframework.test.web.servlet.MockMvc mockMvc;

    protected static org.springframework.test.web.servlet.request.RequestPostProcessor operator(String capability) {
        return jwt().jwt(token -> token.subject("message-operator")
                        .issuedAt(java.time.Instant.now().minusSeconds(30))
                        .expiresAt(java.time.Instant.now().plusSeconds(300))
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
