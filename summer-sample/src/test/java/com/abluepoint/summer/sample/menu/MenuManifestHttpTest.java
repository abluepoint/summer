package com.abluepoint.summer.sample.menu;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuManifestHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void syncsVersionedManifestAndReturnsOnlyEnabledItemsForCurrentSubject() throws Exception {
        String manifest = """
                {"manifestVersion":1,"items":[
                  {"menuId":"dashboard","path":"/dashboard","titleKey":"menu.dashboard","iconKey":"dashboard","sortOrder":20,"capabilityCode":"menu.dashboard.read"},
                  {"menuId":"roles","parentMenuId":"dashboard","path":"/roles","titleKey":"menu.roles","iconKey":"roles","sortOrder":30,"capabilityCode":"menu.roles.read"}
                ]}
                """;
        mockMvc.perform(put("/api/v1/platform/menu-manifest")
                        .contentType(MediaType.APPLICATION_JSON).content(manifest)
                        .header("Idempotency-Key", "manifest-1").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.items[1].capabilityCode").value("menu.roles.read"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest")
                        .with(subject("alice", "menu.dashboard.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].menuId").value("dashboard"))
                .andExpect(jsonPath("$.data.items[0].actions[0]").value("read"));
    }

    @Test
    void manifestSyncIsIdempotentAndRejectsInvalidVersionAndAdminCanOverrideMetadata() throws Exception {
        String body = "{\"manifestVersion\":2,\"items\":[{\"menuId\":\"roles\",\"path\":\"/roles\",\"titleKey\":\"menu.roles\",\"iconKey\":\"roles\",\"sortOrder\":10,\"capabilityCode\":\"menu.roles.read\"}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manifest-2").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manifest-2").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"manifestVersion\":2", "\"manifestVersion\":1"))
                        .header("Idempotency-Key", "manifest-older").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_CONFLICT"));

        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"sortOrder\":90,\"capabilityCode\":\"menu.roles.write\"}")
                        .header("If-Match", "\"1\"")
                        .header("Idempotency-Key", "menu-override-1")
                        .with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(90));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest")
                        .with(enterpriseAdmin("enterprise.menu.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void rejectsUnauthenticatedAndInvalidManifestInput() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"bad id\",\"path\":\"javascript:alert(1)\",\"titleKey\":\"menu.x\",\"iconKey\":\"x\",\"sortOrder\":0}]}" )
                        .header("Idempotency-Key", "manifest-invalid").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_INVALID"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin(String capability) {
        return jwt().jwt(token -> token.subject("platform-admin").claim("platform_capabilities", List.of(capability)))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterpriseAdmin(String capability) {
        return jwt().jwt(token -> token.subject("security-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor subject(String username, String capability) {
        return jwt().jwt(token -> token.subject(username)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
