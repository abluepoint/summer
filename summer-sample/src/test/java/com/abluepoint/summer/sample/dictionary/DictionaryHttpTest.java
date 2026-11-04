package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DictionaryHttpTest {
    // These legacy fixtures now declare their enterprise; production has no implicit sample tenant.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }


    @Autowired
    MockMvc mockMvc;

    @Test
    void administratorCanManageDictionaryAndPublicReadOnlyReturnsPublishedEnabledOptions() throws Exception {
        mockMvc.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"customer-segment","name":"Customer Segment","published":false,"publicReadable":true,"enabled":true}
                                """)
                        .with(dictionaryWriter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("customer-segment"));

        mockMvc.perform(post("/api/dictionaries/customer-segment/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mockMvc, "/api/dictionaries/customer-segment/items", """
                                {"value":"standard","label":"Standard","sortOrder":20,"enabled":true}
                                """))
                        .with(dictionaryWriter()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/dictionaries/customer-segment/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mockMvc, "/api/dictionaries/customer-segment/items", """
                                {"value":"vip","label":"VIP","sortOrder":10,"enabled":false}
                                """))
                        .with(dictionaryWriter()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dictionaries/customer-segment/publish").with(dictionaryWriter())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/enterprises/ent-acme/dictionaries/customer-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].value").value("standard"));

        mockMvc.perform(patch("/api/dictionaries/customer-segment/items/vip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mockMvc, "/api/dictionaries/customer-segment/items/vip", """
                                {"label":"VIP","sortOrder":1,"enabled":true}
                                """))
                        .with(dictionaryWriter()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dictionaries/customer-segment/publish").with(dictionaryWriter())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/enterprises/ent-acme/dictionaries/customer-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].value").value("vip"));
    }

    @Test
    void unpublishedDictionaryAndUnauthorizedManagementUseUnifiedErrors() throws Exception {
        mockMvc.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"internal-only","name":"Internal Only","published":false,"publicReadable":true,"enabled":true}
                                """)
                        .with(dictionaryWriter()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/public/enterprises/ent-acme/dictionaries/internal-only"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isString());

        mockMvc.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"forbidden","name":"Forbidden","published":false,"publicReadable":true,"enabled":true}
                                """)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void invalidDictionaryValueReturnsUnifiedValidationError() throws Exception {
        mockMvc.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"invalid","name":"Invalid","published":false,"publicReadable":true,"enabled":true}
                                """)
                        .with(dictionaryWriter()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/dictionaries/invalid/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mockMvc, "/api/dictionaries/invalid/items", """
                                {"value":"bad value","label":"Bad","sortOrder":0,"enabled":true}
                                """))
                        .with(dictionaryWriter()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_INVALID"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor dictionaryWriter() {
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
}
