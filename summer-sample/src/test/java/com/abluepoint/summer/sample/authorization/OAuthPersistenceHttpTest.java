package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerOAuthClientStore;
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
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class OAuthPersistenceHttpTest {
    @Autowired MockMvc mockMvc;
    @Autowired CurrentManagedAuthorizationTestFixture managed;
    @Autowired JdbcTemplate jdbc;
    @Autowired SummerOAuthClientStore clients;
    @Autowired SummerAuthorizationStore authorizations;
    @Autowired SummerConsentStore consents;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.abluepoint.summer.security.audit.SecurityAuditLogger audit;

    @BeforeEach
    void createJdbcSchema() {
        managed.grant(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "user", "security.oauth-client.read", "security.oauth-client.write");
        jdbc.execute("create table if not exists oauth2_registered_client (id varchar(100) primary key, client_id varchar(100) unique not null, client_id_issued_at timestamp(6) not null, client_secret varchar(200), client_secret_expires_at timestamp(6), client_name varchar(200) not null, client_authentication_methods varchar(1000) not null, authorization_grant_types varchar(1000) not null, redirect_uris varchar(2000), post_logout_redirect_uris varchar(2000), scopes varchar(1000) not null, client_settings varchar(2000) not null, token_settings varchar(2000) not null)");
        jdbc.execute("create table if not exists oauth2_authorization_consent (registered_client_id varchar(100) not null, principal_name varchar(200) not null, authorities varchar(1000) not null, primary key (registered_client_id, principal_name))");
        jdbc.execute("create table if not exists summer_oauth_client_status (client_id varchar(100) primary key, status varchar(20) not null, version integer not null, updated_at timestamp(6) not null)");
        jdbc.update("delete from oauth2_authorization_consent");
        jdbc.update("delete from oauth2_registered_client");
        jdbc.update("delete from summer_oauth_client_status");
    }

    @Test
    void businessFailuresKeepTheirAuditWhileUsingTheUnifiedBoundary() throws Exception {
        createEditableClient();
        mockMvc.perform(post("/api/security/oauth-clients").with(manager()).contentType("application/json")
                        .header("X-Request-Id", "oauth-duplicate")
                        .content("{\"clientId\":\"editable-client\",\"clientName\":\"Duplicate\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/other\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isConflict())
                .andExpect(UnifiedBusinessFailure.unified("SUMMER_OAUTH_CLIENT_EXISTS", "Client ID already exists"));
        mockMvc.perform(patch("/api/security/oauth-clients/missing").with(manager()).contentType("application/json")
                        .header("X-Request-Id", "oauth-missing").content("{\"version\":1,\"status\":\"DISABLED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(UnifiedBusinessFailure.unified("SUMMER_OAUTH_CLIENT_NOT_FOUND", "Client was not found"));
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .header("X-Request-Id", "oauth-stale").content("{\"version\":2,\"status\":\"DISABLED\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(UnifiedBusinessFailure.unified("SUMMER_OAUTH_CLIENT_VERSION_CONFLICT", "Client version has changed; reload before retrying"));
        mockMvc.perform(patch("/api/security/oauth-clients/editable-client").with(manager()).contentType("application/json")
                        .header("X-Request-Id", "oauth-invalid").content("{\"version\":0,\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(UnifiedBusinessFailure.unified("SUMMER_OAUTH_CLIENT_INVALID", "version is required"))
                .andExpect(result -> assertThat(result.getResolvedException().getCause()).isInstanceOf(IllegalArgumentException.class));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/security/oauth-consents/missing/alice").with(manager()).header("X-Request-Id", "consent-missing"))
                .andExpect(status().isNotFound())
                .andExpect(UnifiedBusinessFailure.unified("SUMMER_OAUTH_CONSENT_NOT_FOUND", "Authorization consent was not found"));
        org.mockito.Mockito.verify(audit).record(org.mockito.ArgumentMatchers.eq("oauth_management_failed"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("code", "SUMMER_OAUTH_CLIENT_EXISTS", "requestId", "oauth-duplicate")));
        org.mockito.Mockito.verify(audit).record(org.mockito.ArgumentMatchers.eq("oauth_management_failed"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("code", "SUMMER_OAUTH_CLIENT_NOT_FOUND", "requestId", "oauth-missing")));
        org.mockito.Mockito.verify(audit).record(org.mockito.ArgumentMatchers.eq("oauth_management_failed"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("code", "SUMMER_OAUTH_CLIENT_VERSION_CONFLICT", "requestId", "oauth-stale")));
        org.mockito.Mockito.verify(audit).record(org.mockito.ArgumentMatchers.eq("oauth_management_failed"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("code", "SUMMER_OAUTH_CLIENT_INVALID", "requestId", "oauth-invalid")));
        org.mockito.Mockito.verify(audit).record(org.mockito.ArgumentMatchers.eq("oauth_management_failed"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Map.of("code", "SUMMER_OAUTH_CONSENT_NOT_FOUND", "requestId", "consent-missing")));
        mockMvc.perform(get("/api/security/oauth-clients").with(manager()))
                .andExpect(jsonPath("$.data.items[0].clientName").value("Before"))
                .andExpect(jsonPath("$.data.items[0].version").value(1));
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
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("user").claim("scope", "security.oauth-client.read")).authorities(
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
        var admin = SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("user").claim("scope", "security.oauth-client.read security.oauth-client.write")).authorities(
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
        var admin = SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("user").claim("scope", "security.oauth-client.read security.oauth-client.write")).authorities(
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
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("user").claim("scope", "security.oauth-client.read")).authorities(
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

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor manager() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(t -> t.subject("user").claim("scope", "security.oauth-client.read security.oauth-client.write")).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.write"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.oauth-client.read"));
    }

    private void createEditableClient() throws Exception {
        mockMvc.perform(post("/api/security/oauth-clients").with(manager()).contentType("application/json")
                        .content("{\"clientId\":\"editable-client\",\"clientName\":\"Before\",\"publicClient\":true,\"redirectUris\":[\"https://example.test/callback\"],\"scopes\":[\"openid\"]}"))
                .andExpect(status().isCreated());
    }

}
