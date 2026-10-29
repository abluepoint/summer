package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.infrastructure.JdbcOAuthStorageConfiguration;
import com.abluepoint.summer.sample.authorization.infrastructure.JdbcSummerOAuthClientStore;

import com.abluepoint.summer.security.oauth2.spi.SummerAuthorizationStore;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
import com.abluepoint.summer.security.oauth2.spi.SummerOAuthClientStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "summer.security.storage.oauth-client=jdbc",
        "summer.security.storage.authorization=jdbc",
        "summer.security.storage.consent=jdbc"
})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(OAuthPersistenceHttpTest.AuditConfiguration.class)
class OAuthPersistenceHttpTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SummerOAuthClientStore clients;
    @Autowired SummerAuthorizationStore authorizations;
    @Autowired SummerConsentStore consents;
    @Autowired OAuthAuditCapture auditCapture;

    @BeforeEach
    void createJdbcSchema() {
        auditCapture.events.clear();
        jdbc.execute("create table if not exists oauth2_registered_client (id varchar(100) primary key, client_id varchar(100) unique not null, client_id_issued_at timestamp(6) not null, client_secret varchar(200), client_secret_expires_at timestamp(6), client_name varchar(200) not null, client_authentication_methods varchar(1000) not null, authorization_grant_types varchar(1000) not null, redirect_uris varchar(2000), post_logout_redirect_uris varchar(2000), scopes varchar(1000) not null, client_settings varchar(2000) not null, token_settings varchar(2000) not null)");
        jdbc.execute("create table if not exists oauth2_authorization_consent (registered_client_id varchar(100) not null, principal_name varchar(200) not null, authorities varchar(1000) not null, primary key (registered_client_id, principal_name))");
        jdbc.execute("create table if not exists summer_oauth_client_status (client_id varchar(100) primary key, status varchar(20) not null, version integer not null, updated_at timestamp(6) not null)");
        jdbc.update("delete from oauth2_authorization_consent");
        jdbc.update("delete from oauth2_registered_client");
        jdbc.update("delete from summer_oauth_client_status");
    }

    @Test
    void usesPersistentStoresAndProtectsClientManagement() throws Exception {
        assertThat(clients).isNotNull();
        assertThat(authorizations).isNotNull();
        assertThat(consents).isNotNull();
        assertThat(clients.getClass().getName()).contains("Jdbc");
        assertThat(authorizations.getClass().getName()).contains("Jdbc");
        assertThat(consents.getClass().getName()).contains("Jdbc");

        mockMvc.perform(get("/api/security/oauth-clients")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/security/oauth-clients")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "SCOPE_security.oauth-client.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void clientAndConsentCanBeWrittenAndReadThroughJdbcContracts() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("persisted-client")
                .clientName("Persisted client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/callback")
                .scope("openid")
                .build();
        clients.save(client);
        assertThat(clients.findByClientId("persisted-client").getClientName()).isEqualTo("Persisted client");

        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent.withId(client.getId(), "admin")
                .authority(new org.springframework.security.core.authority.SimpleGrantedAuthority("openid"))
                .build();
        consents.save(consent);
        assertThat(consents.findById(client.getId(), "admin").getAuthorities())
                .extracting(Object::toString).contains("openid");
    }

    @Test
    void managesPublicClientLifecycleWithoutReturningSecrets() throws Exception {
        var admin = SecurityMockMvcRequestPostProcessors.jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.read"));
        mockMvc.perform(post("/api/security/oauth-clients").with(admin)
                        .contentType("application/json")
                        .content("{\"clientId\":\"managed-client\",\"clientName\":\"Managed client\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/callback\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.data.clientId").value("managed-client"))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());
        mockMvc.perform(patch("/api/security/oauth-clients/managed-client").with(admin)
                        .contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }
    @Test
    void editsClientMetadataWithVersionAndPreservesPublicPkceProtocol() throws Exception {
        var admin = SecurityMockMvcRequestPostProcessors.jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.read"));
        mockMvc.perform(post("/api/security/oauth-clients").with(admin).contentType("application/json")
                        .content("{\"clientId\":\"editable-client\",\"clientName\":\"Before\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/callback\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(admin).contentType("application/json")
                        .content("{\"clientName\":\"After\",\"redirectUris\":[\"https://example.test/new-callback\"],\"scopes\":[\"openid\",\"profile\"],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(header().exists("X-Request-Id"));
        mockMvc.perform(get("/api/security/oauth-clients").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].clientName").value("After"))
                .andExpect(jsonPath("$.data.items[0].redirectUris[0]").value("https://example.test/new-callback"))
                .andExpect(jsonPath("$.data.items[0].scopes").value(org.hamcrest.Matchers.containsInAnyOrder("openid", "profile")))
                .andExpect(jsonPath("$.data.items[0].version").value(2))
                .andExpect(jsonPath("$.data.items[0].authenticationMethods[0]").value("none"))
                .andExpect(jsonPath("$.data.items[0].grantTypes[0]").value("authorization_code"))
                .andExpect(jsonPath("$.data.items[0].clientSecret").doesNotExist());
        assertThat(clients.findByClientId("editable-client").getClientSettings().isRequireProofKey()).isTrue();
        assertThat(clients.findByClientId("editable-client").getClientSettings().isRequireAuthorizationConsent()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"clientName\":\"   \"",
            "\"redirectUris\":[]",
            "\"redirectUris\":[\"javascript:alert(1)\"]",
            "\"redirectUris\":[\"http://example.test/callback\"]",
            "\"redirectUris\":[\"https://example.test/callback#fragment\"]",
            "\"redirectUris\":[\"https://*.example.test/callback\"]",
            "\"redirectUris\":[\"https://example.test/callback,https://other.test/callback\"]",
            "\"redirectUris\":[null]",
            "\"scopes\":[]",
            "\"scopes\":[\"openid profile\"]",
            "\"scopes\":[\"openid,profile\"]"
    })
    void rejectsInvalidMetadataWithoutChangingVersion(String fields) throws Exception {
        createEditableClient();
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .content("{" + fields + ",\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CLIENT_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Before"))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
    }

    @Test
    void staleMetadataOrStatusCannotOverwriteTheCurrentClient() throws Exception {
        createEditableClient();
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .content("{\"clientName\":\"Current\",\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        for (String fields : new String[]{"\"clientName\":\"Stale\"", "\"status\":\"DISABLED\""}) {
            mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                            .content("{" + fields + ",\"version\":1}"))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CLIENT_VERSION_CONFLICT"))
                    .andExpect(header().exists("X-Request-Id"));
        }
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Current"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].version").value(2));
    }

    @Test
    void metadataWritesRequirePermissionAndVersionAndKnownClient() throws Exception {
        createEditableClient();
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.read")))
                        .contentType("application/json").content("{\"clientName\":\"Denied\",\"version\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager())
                        .contentType("application/json").content("{\"clientName\":\"No version\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/security/oauth-clients/missing").with(manager())
                        .contentType("application/json").content("{\"clientName\":\"Missing\",\"version\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Before"))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
    }

    @Test
    void twoConcurrentWritersShareOneMetadataAndStatusVersion() throws Exception {
        createEditableClient();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                gate.await();
                return mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager())
                                .contentType("application/json").content("{\"clientName\":\"Writer " + index + "\",\"version\":1}"))
                        .andReturn().getResponse().getStatus();
            })).toList();
            gate.countDown();
            assertThat(java.util.List.of(futures.get(0).get(), futures.get(1).get()))
                    .containsExactlyInAnyOrder(200, 412);
        }
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].version").value(2));
    }

    @Test
    void configuredClientSeedingPreservesDisabledMetadataAcrossRestart() throws Exception {
        createEditableClient();
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager())
                        .contentType("application/json").content("{\"clientName\":\"Persisted disabled client\",\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isOk());
        var properties = new com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties();
        var configured = new com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties.Client();
        configured.setId("editable-client"); configured.setPublicClient(true);
        configured.setGrantTypes(java.util.List.of("authorization_code"));
        configured.setRedirectUris(java.util.List.of("https://example.test/callback"));
        properties.getAuthorizationServer().setClients(java.util.List.of(configured));
        new JdbcOAuthStorageConfiguration().seedConfiguredClients(properties, (JdbcSummerOAuthClientStore) clients,
                        org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder())
                .run(new org.springframework.boot.DefaultApplicationArguments());
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Persisted disabled client"))
                .andExpect(jsonPath("$.data.items[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data.items[0].version").value(2));
        assertThat(clients.findByClientId("editable-client")).isNull();
    }

    @Test
    void duplicateClientRegistrationIsAConflictAndDoesNotReplaceMetadata() throws Exception {
        createEditableClient();
        mockMvc.perform(post("/api/security/oauth-clients").with(manager()).contentType("application/json")
                        .content("{\"clientId\":\"editable-client\",\"clientName\":\"Duplicate\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/other\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CLIENT_EXISTS"));
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Before"))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
    }

    @Test
    void preservesFailurePriorityBeforeAndAfterTheVersionFence() throws Exception {
        createEditableClient();
        mockMvc.perform(post("/api/security/oauth-clients").with(manager()).contentType("application/json")
                        .content("{\"clientId\":\"invalid-public\",\"clientName\":\"Invalid\",\"publicClient\":false,\"redirectUris\":[\"http://example.test/callback\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("only public PKCE clients are supported by this endpoint"));
        mockMvc.perform(patch("/api/security/oauth-clients/missing").with(manager()).contentType("application/json")
                        .content("{\"version\":1,\"status\":\"UNKNOWN\",\"clientName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("clientName must contain 1 to 200 characters without control characters"));
        mockMvc.perform(patch("/api/security/oauth-clients/missing").with(manager()).contentType("application/json")
                        .content("{\"version\":1,\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CLIENT_NOT_FOUND"));
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .content("{\"version\":2,\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CLIENT_VERSION_CONFLICT"));
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .content("{\"version\":1,\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("status must be ACTIVE or DISABLED"));
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
    }

    @Test
    void managementAuditRetainsActorRequestIdAndOutcome() throws Exception {
        var auditor = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("oauth-auditor"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write"));
        String body = "{\"clientId\":\"audit-client\",\"clientName\":\"Audit client\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/callback\"],\"scopes\":[\"openid\"]}";
        mockMvc.perform(post("/api/security/oauth-clients").with(auditor).header("X-Request-Id", "oauth-audit-create")
                        .contentType("application/json").content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/security/oauth-clients").with(auditor).header("X-Request-Id", "oauth-audit-duplicate")
                        .contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(auditCapture.events.stream().filter(event -> event.event().equals("oauth_client_created")).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.principal()).isEqualTo("oauth-auditor");
                    assertThat(event.attributes()).isEqualTo(java.util.Map.of("clientId", "audit-client", "requestId", "oauth-audit-create", "publicClient", true));
                });
        assertThat(auditCapture.events.stream().filter(event -> event.event().equals("oauth_management_failed")).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.principal()).isEqualTo("oauth-auditor");
                    assertThat(event.attributes()).isEqualTo(java.util.Map.of("code", "SUMMER_OAUTH_CLIENT_EXISTS", "requestId", "oauth-audit-duplicate"));
                });
    }

    @Test
    void consentListAndRevocationRetainRegisteredClientIdentityAndMissingResult() throws Exception {
        createEditableClient();
        String registeredId = clients.findByClientId("editable-client").getId();
        consents.save(OAuth2AuthorizationConsent.withId(registeredId, "consent-principal")
                .authority(new org.springframework.security.core.authority.SimpleGrantedAuthority("openid")).build());
        mockMvc.perform(get("/api/security/oauth-consents").with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].clientId").value(registeredId))
                .andExpect(jsonPath("$.data.items[0].principalName").value("consent-principal"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/security/oauth-consents/{clientId}/{principalName}", registeredId, "consent-principal").with(manager()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/security/oauth-consents/{clientId}/{principalName}", registeredId, "consent-principal").with(manager()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("SUMMER_OAUTH_CONSENT_NOT_FOUND"));
        mockMvc.perform(get("/api/security/oauth-consents").with(manager()))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class AuditConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        OAuthAuditCapture oauthAuditCapture() { return new OAuthAuditCapture(); }
    }

    static class OAuthAuditCapture implements com.abluepoint.summer.security.audit.spi.SecurityAuditLogger {
        private final java.util.List<AuditEntry> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void record(String event, String principal, java.util.Map<String, ?> attributes) {
            events.add(new AuditEntry(event, principal, java.util.Map.copyOf(attributes)));
        }
    }
    private record AuditEntry(String event, String principal, java.util.Map<String, ?> attributes) {}

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor manager() {
        return SecurityMockMvcRequestPostProcessors.jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.read"));
    }

    private void createEditableClient() throws Exception {
        mockMvc.perform(post("/api/security/oauth-clients").with(manager()).contentType("application/json")
                        .content("{\"clientId\":\"editable-client\",\"clientName\":\"Before\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/callback\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isCreated());
    }

}
