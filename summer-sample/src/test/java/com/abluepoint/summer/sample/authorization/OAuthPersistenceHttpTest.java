package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerOAuthClientStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
class OAuthPersistenceHttpTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SummerOAuthClientStore clients;
    @Autowired SummerAuthorizationStore authorizations;
    @Autowired SummerConsentStore consents;

    @BeforeEach
    void createJdbcSchema() {
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
}
