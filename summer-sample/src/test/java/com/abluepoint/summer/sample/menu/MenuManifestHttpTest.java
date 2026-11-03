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
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_CONFLICT"))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_MENU_MANIFEST_CONFLICT", "Manifest version must increase"));

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
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_INVALID"))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_MENU_MANIFEST_INVALID", "Invalid menuId"));
    }

    @Test
    void platformAdministratorCanReadCurrentManifestWithoutWritingIt() throws Exception {
        String body = "{\"manifestVersion\":7,\"items\":[{\"menuId\":\"root\",\"path\":\"/root\",\"titleKey\":\"menu.root\",\"iconKey\":\"root\",\"sortOrder\":10}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "read-current").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/platform/menu-manifest").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.data.items[0].menuId").value("root"));
        mockMvc.perform(get("/api/v1/platform/menu-manifest").with(subject("ordinary", "customer.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void managementReadIncludesDisabledItemsAndUsesOverrideVersionForEditing() throws Exception {
        String body = "{\"manifestVersion\":7,\"items\":[{\"menuId\":\"root\",\"path\":\"/root\",\"titleKey\":\"menu.root\",\"iconKey\":\"root\",\"sortOrder\":10},{\"menuId\":\"child\",\"parentMenuId\":\"root\",\"path\":\"/child\",\"titleKey\":\"menu.child\",\"iconKey\":\"child\",\"sortOrder\":20}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manage-current").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].version").value(1));
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/child").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"parentMenuId\":\"\",\"sortOrder\":3,\"capabilityCode\":\"customer.write\"}")
                        .header("Idempotency-Key", "manage-disable").header("If-Match", "\"1\"").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentMenuId").doesNotExist());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].menuId").value("child"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].capabilityCode").value("customer.write"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(subject("ordinary", "customer.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesDescendantsOfDisabledParentsAndRejectsMissingOrStaleOverrideVersion() throws Exception {
        String body = "{\"manifestVersion\":5,\"items\":[{\"menuId\":\"parent\",\"path\":\"/parent\",\"titleKey\":\"menu.parent\",\"iconKey\":\"parent\",\"sortOrder\":10},{\"menuId\":\"child\",\"parentMenuId\":\"parent\",\"path\":\"/child\",\"titleKey\":\"menu.child\",\"iconKey\":\"child\",\"sortOrder\":20}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "parents").with(platformAdmin("platform.menu.write"))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}").header("Idempotency-Key", "disable-parent").header("If-Match", "\"1\"")
                        .with(enterpriseAdmin("enterprise.menu.write"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(subject("reader", "customer.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}").header("Idempotency-Key", "missing-version").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_PRECONDITION_FAILED", "The resource has changed"));
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}").header("Idempotency-Key", "enable-parent").header("If-Match", "\"2\"")
                        .with(enterpriseAdmin("enterprise.menu.write"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3));
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}").header("Idempotency-Key", "stale-version").header("If-Match", "\"1\"")
                        .with(enterpriseAdmin("enterprise.menu.write"))).andExpect(status().isPreconditionFailed())
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_PRECONDITION_FAILED", "The resource has changed"));
    }

    @Test
    void acceptsNestedFrontendRouteIdsAndReplaysMetadataRegardlessOfArrayOrder() throws Exception {
        String root = "{\"menuId\":\"dictionary\",\"path\":\"/dictionary\",\"titleKey\":\"menu.dictionary\",\"iconKey\":\"dictionary\",\"sortOrder\":10}";
        String child = "{\"menuId\":\"dictionary/form\",\"parentMenuId\":\"dictionary\",\"path\":\"/dictionary/form\",\"titleKey\":\"menu.form\",\"iconKey\":\"form\",\"sortOrder\":20}";
        String body = "{\"manifestVersion\":2,\"items\":[" + child + "," + root + "]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "nested-order").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestVersion\":2,\"items\":[" + root + "," + child + "]}")
                        .header("Idempotency-Key", "nested-order").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].menuId").value("dictionary/form"));
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest").param("menuId", "dictionary/form")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "nested-override")
                        .with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.menuId").value("dictionary/form"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void firstOverrideAdvancesVersionAndAStaleInitialEditorCannotOverwriteIt() throws Exception {
        String body = "{\"manifestVersion\":1,\"items\":[{\"menuId\":\"root\",\"path\":\"/root\",\"titleKey\":\"menu.root\",\"iconKey\":\"root\",\"sortOrder\":10}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "first-override").with(platformAdmin("platform.menu.write"))).andExpect(status().isOk());
        for (int retry = 0; retry < 2; retry++) {
            mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/root").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}").header("If-Match", "\"1\"").header("Idempotency-Key", "same-operation")
                            .with(enterpriseAdmin("enterprise.menu.write")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        }
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/root").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}").header("If-Match", "\"1\"").header("Idempotency-Key", "stale-initial-editor")
                        .with(enterpriseAdmin("enterprise.menu.write"))).andExpect(status().isPreconditionFailed())
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_PRECONDITION_FAILED", "The resource has changed"));
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"manifestVersion\":1", "\"manifestVersion\":2").replace("\"sortOrder\":10", "\"sortOrder\":50"))
                        .header("Idempotency-Key", "next-manifest").with(platformAdmin("platform.menu.write"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].sortOrder").value(50));
    }

    @Test
    void customerActionsUsePersistedOperationGrantsInsteadOfRequestedWriteScope() throws Exception {
        customerActions("[\"GET:/api/customers\"]", "[]", "read");
    }

    @Test
    void explicitDenyRemovesCreateEvenWhenTheRoleAllowsIt() throws Exception {
        customerActions("[\"GET:/api/customers\",\"POST:/api/customers\"]", "[\"POST:/api/customers\"]", "read");
    }

    @Test
    void allowedCreateIsProjectedButUnassignedUpdateAndDeleteAreNot() throws Exception {
        customerActions("[\"GET:/api/customers\",\"POST:/api/customers\"]", "[]", "read", "create");
    }

    private void customerActions(String operations, String denied, String... expected) throws Exception {
        mockMvc.perform(put("/api/v1/platform/menu-manifest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"customer/drawer\",\"path\":\"/customer/drawer\",\"titleKey\":\"menu.customer\",\"iconKey\":\"customer\",\"sortOrder\":1}]}")
                        .header("Idempotency-Key", "customer-actions-manifest").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        String capabilities = operations.replace("[", "[\"enterprise.menu.read\",");
        MvcResult role = mockMvc.perform(post("/api/v1/enterprises/ent-acme/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Actions\",\"code\":\"actions\",\"capabilityIds\":" + capabilities + "}")
                        .header("Idempotency-Key", "action-role").with(enterpriseAdmin("enterprise.roles.write")))
                .andExpect(status().isCreated()).andReturn();
        String roleId = JsonPath.read(role.getResponse().getContentAsString(), "$.data.roleId");
        mockMvc.perform(put("/api/v1/enterprises/ent-acme/users/action-reader/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + roleId + "\"],\"deniedCapabilityCodes\":" + denied + "}")
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "action-binding").with(enterpriseAdmin("enterprise.roles.write")))
                .andExpect(status().isOk());
        var user = jwt().jwt(token -> token.subject("action-reader").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.write"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actions").value(org.hamcrest.Matchers.contains(expected)));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/menu-manifest").with(user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(jwt().jwt(token -> token.subject("action-reader")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "SUSPENDED"))))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden());
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
