package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuManifestHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate menuDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources interfaces;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    private final Map<String, String> menuRoles = new java.util.HashMap<>();

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        menuDb.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id");
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(menuDb, transactions, interfaces);
        managed.identity("PLATFORM:platform", "platform-admin");
        current.grantHandlers(Domain.platform(), "platform-admin", (handler, path, method) ->
                handler.getBeanType().equals(MenuManifestController.class)
                        && path.equals("/api/v1/platform/menu-manifest") && java.util.Set.of("GET", "PUT").contains(method));
        for (String subject : List.of("security-admin", "alice", "reader", "action-reader")) {
            managed.identity("ENTERPRISE:ent-acme", subject);
            if (subject.equals("security-admin"))
                menuRoles.put(subject, current.grantHandlers(Domain.enterprise("ent-acme"), subject, (handler, path, method) ->
                        handler.getBeanType().equals(MenuManifestController.class)
                                && path.startsWith("/api/v1/enterprises/{enterpriseId}/menu-manifest")
                                && (method.equals("GET") || method.equals("PATCH"))));
            else
                menuRoles.put(subject, menuDb.queryForObject("select role_id from summer_permission_user_role where domain_key='ENTERPRISE:ent-acme' and username=?", String.class, subject));
        }
        managed.identity("PLATFORM:platform", "ordinary");
        managed.identity("ENTERPRISE:ent-acme", "ordinary");
    }

    @org.junit.jupiter.api.AfterEach void releaseManagedAuthorization() { managed.close(); }

    private void grantMenus(String subject, String... menuIds) {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String role = menuRoles.get(subject);
            String permission = "menu-fixture-" + java.util.UUID.randomUUID();
            menuDb.update("insert into summer_permission_group(domain_key,group_id,name,sort_order,version) values ('ENTERPRISE:ent-acme',?,'Explicit MENU fixture',0,1)", permission);
            menuDb.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values ('ENTERPRISE:ent-acme',?,?,'MENU')", permission, permission);
            menuDb.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version) values ('ENTERPRISE:ent-acme',?,?,'Explicit menu fixture','MENU','AUTHORIZED',?,'ACTIVE',1)", permission, permission, permission);
            menuDb.update("insert into summer_role_permission(domain_key,role_id,permission_id) values ('ENTERPRISE:ent-acme',?,?)", role, permission);
            for (String menuId : menuIds)
                menuDb.update("insert into summer_permission_resource(domain_key,permission_id,resource_type,resource_id) values ('ENTERPRISE:ent-acme',?,'MENU',?)", permission, menuId);
            menuDb.update("update summer_authorization_domain set revision=revision+1 where domain_key='ENTERPRISE:ent-acme'");
        });
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void syncsVersionedManifestAndReturnsOnlyEnabledItemsForCurrentSubject() throws Exception {
        String manifest = """
                {"manifestVersion":1,"items":[
                  {"menuId":"dashboard","path":"/dashboard","titleKey":"menu.dashboard","iconKey":"dashboard","sortOrder":20},
                  {"menuId":"roles","parentMenuId":"dashboard","path":"/roles","titleKey":"menu.roles","iconKey":"roles","sortOrder":30}
                ]}
                """;
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(manifest)
                        .header("Idempotency-Key", "manifest-1").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.items[1].titleKey").value("menu.roles"));
        grantMenus("alice", "dashboard");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest")
                        .with(subject("alice", "menu.dashboard.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].menuId").value("dashboard"))
                .andExpect(jsonPath("$.data.items[0].pageEligible").value(true))
                .andExpect(jsonPath("$.data.items[0].actions").isEmpty());
    }

    @Test
    void manifestSyncIsIdempotentAndRejectsInvalidVersionAndAdminCanOverrideMetadata() throws Exception {
        String body = "{\"manifestVersion\":2,\"items\":[{\"menuId\":\"roles\",\"path\":\"/roles\",\"titleKey\":\"menu.roles\",\"iconKey\":\"roles\",\"sortOrder\":10}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manifest-2").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manifest-2").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"2\"").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"manifestVersion\":2", "\"manifestVersion\":1"))
                        .header("Idempotency-Key", "manifest-older").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_CONFLICT"))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_MENU_MANIFEST_CONFLICT", "Manifest version must increase"));

        grantMenus("security-admin", "roles");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"sortOrder\":90}")
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
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"bad id\",\"path\":\"javascript:alert(1)\",\"titleKey\":\"menu.x\",\"iconKey\":\"x\",\"sortOrder\":0}]}" )
                        .header("Idempotency-Key", "manifest-invalid").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_MENU_MANIFEST_INVALID"))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_MENU_MANIFEST_INVALID", "Invalid menuId"));
    }

    @Test
    void platformAdministratorCanReadCurrentManifestWithoutWritingIt() throws Exception {
        String body = "{\"manifestVersion\":7,\"items\":[{\"menuId\":\"root\",\"path\":\"/root\",\"titleKey\":\"menu.root\",\"iconKey\":\"root\",\"sortOrder\":10}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
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
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "manage-current").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].version").value(1));
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/child").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"parentMenuId\":\"\",\"sortOrder\":3}")
                        .header("Idempotency-Key", "manage-disable").header("If-Match", "\"1\"").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.parentMenuId").doesNotExist());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].menuId").value("child"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].capabilityCode").doesNotExist());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(subject("ordinary", "customer.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesDescendantsOfDisabledParentsAndRejectsMissingOrStaleOverrideVersion() throws Exception {
        String body = "{\"manifestVersion\":5,\"items\":[{\"menuId\":\"parent\",\"path\":\"/parent\",\"titleKey\":\"menu.parent\",\"iconKey\":\"parent\",\"sortOrder\":10},{\"menuId\":\"child\",\"parentMenuId\":\"parent\",\"path\":\"/child\",\"titleKey\":\"menu.child\",\"iconKey\":\"child\",\"sortOrder\":20}]}";
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "parents").with(platformAdmin("platform.menu.write"))).andExpect(status().isOk());
        grantMenus("reader", "parent", "child");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}").header("Idempotency-Key", "disable-parent").header("If-Match", "\"1\"")
                        .with(enterpriseAdmin("enterprise.menu.write"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(subject("reader", "customer.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/parent").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}").header("Idempotency-Key", "missing-version").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().is(428))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SAMPLE_PERMISSION_CONDITION_REQUIRED", "If-Match is required"));
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
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "nested-order").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
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
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)
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
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"manifestVersion\":1", "\"manifestVersion\":2").replace("\"sortOrder\":10", "\"sortOrder\":50"))
                        .header("Idempotency-Key", "next-manifest").with(platformAdmin("platform.menu.write"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest/management").with(enterpriseAdmin("enterprise.menu.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].enabled").value(false))
                .andExpect(jsonPath("$.data.items[0].sortOrder").value(50));
    }

    @Test
    void customerActionsUsePersistedOperationGrantsInsteadOfRequestedWriteScope() throws Exception {
        customerActions(false, false);
    }

    @Test
    void revokedCurrentCreateGrantImmediatelyRemovesTheOperation() throws Exception {
        customerActions(true, true);
    }

    @Test
    void allowedCreateIsProjectedButUnassignedUpdateAndDeleteAreNot() throws Exception {
        customerActions(true, false);
    }

    private void customerActions(boolean createGranted, boolean revokeCreate) throws Exception {
        mockMvc.perform(put("/api/v1/platform/menu-manifest").header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"customer/drawer\",\"path\":\"/customer/drawer\",\"titleKey\":\"menu.customer\",\"iconKey\":\"customer\",\"sortOrder\":1}]}")
                        .header("Idempotency-Key", "customer-actions-manifest").with(platformAdmin("platform.menu.write")))
                .andExpect(status().isOk());
        grantMenus("action-reader", "customer/drawer");
        managed.identity("PLATFORM:platform", "action-reader");
        current.grantHandlers(Domain.platform(), "action-reader", (handler, path, method) ->
                handler.getBeanType().getSimpleName().equals("CustomerController")
                        && path.equals("/api/customers") && method.equals("GET"));
        // MENU has no interface actions; operation hints and calls use independently persisted INTERFACE grants.
        var user = jwt().jwt(token -> token.subject("action-reader").claim("scope", "customer.read customer.write")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.write"));
        if (createGranted) {
            String createRole = current.grantHandlers(Domain.platform(), "action-reader", (handler, path, method) ->
                    handler.getBeanType().getSimpleName().equals("CustomerController")
                            && path.equals("/api/customers") && method.equals("POST"));
            if (revokeCreate) {
                mockMvc.perform(get("/api/v1/platform/authorization/operations").param("actions", "customer.create").with(user))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.actions['customer.create']").value(true));
                current.revoke(Domain.platform(), "action-reader", createRole);
            }
        }
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].menuId").value("customer/drawer"))
                .andExpect(jsonPath("$.data.items[0].pageEligible").value(true))
                .andExpect(jsonPath("$.data.items[0].actions").isEmpty());
        mockMvc.perform(get("/api/v1/platform/authorization/operations")
                        .param("actions", "customer.read", "customer.create", "customer.update", "customer.delete").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actions['customer.read']").value(true))
                .andExpect(jsonPath("$.data.actions['customer.create']").value(createGranted && !revokeCreate))
                .andExpect(jsonPath("$.data.actions['customer.update']").value(false))
                .andExpect(jsonPath("$.data.actions['customer.delete']").value(false));
        mockMvc.perform(get("/api/customers").with(user)).andExpect(status().isOk());
        mockMvc.perform(post("/api/customers").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"current-action-customer\",\"email\":\"action@example.test\",\"displayName\":\"Action\"}"))
                .andExpect(createGranted && !revokeCreate ? status().isOk() : status().isForbidden());
        mockMvc.perform(patch("/api/customers/current-action-customer").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Unassigned\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/customers/current-action-customer").with(user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-other/menu-manifest").with(user)).andExpect(status().isForbidden());
        menuDb.update("update summer_enterprise_member set status='SUSPENDED',version=version+1 where enterprise_id='ent-acme' and subject='action-reader'");
        menuDb.update("update summer_authorization_domain set revision=revision+1 where domain_key='ENTERPRISE:ent-acme'");
        // Reuse the original authentication after persisted suspension; stale ACTIVE claims cannot keep access.
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(user)).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin(String capability) {
        return jwt().jwt(token -> token.subject("platform-admin").claim("scope", capability).claim("platform_capabilities", List.of(capability)))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterpriseAdmin(String capability) {
        return jwt().jwt(token -> token.subject("security-admin").claim("scope", capability)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor subject(String username, String capability) {
        return jwt().jwt(token -> token.subject(username).claim("scope", capability)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
