package com.abluepoint.summer.security;

import com.abluepoint.summer.security.identity.spi.SummerIdentityStore;
import com.abluepoint.summer.security.identity.internal.DefaultSummerIdentityStore;
import com.abluepoint.summer.security.oauth2.spi.SummerOAuthClientStore;
import com.abluepoint.summer.security.oauth2.spi.SummerAuthorizationStore;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
import com.abluepoint.summer.security.oauth2.internal.DefaultSummerOAuthClientStore;
import com.abluepoint.summer.security.oauth2.internal.DefaultSummerAuthorizationStore;
import com.abluepoint.summer.security.oauth2.internal.DefaultSummerConsentStore;
import com.abluepoint.summer.security.key.spi.SummerKeyStore;
import com.abluepoint.summer.security.key.internal.DefaultSummerKeyStore;
import com.abluepoint.summer.security.session.spi.SummerSessionStore;
import com.abluepoint.summer.security.session.internal.DefaultSummerSessionStore;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Runtime store behavior with only the capability's dependency graph, without Boot assembly. */
class SecurityStoreCapabilityTest {
    @Test
    void identityAdapterPreservesTheApplicationsIdentitySource() {
        var user = User.withUsername("capability-user").password("").authorities("read").build();
        SummerIdentityStore identities = new DefaultSummerIdentityStore(new InMemoryUserDetailsManager(user));
        assertThat(identities.loadUserByUsername("capability-user").getUsername()).isEqualTo("capability-user");
        assertThat(identities.loadUserByUsername("capability-user").getAuthorities())
                .extracting(Object::toString).containsExactly("read");
    }

    @Test
    void oauthAdaptersRetainClientAuthorizationAndConsentLifecycle() {
        var client = RegisteredClient.withId("capability-client").clientId("capability-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/callback").build();
        SummerOAuthClientStore clients = new DefaultSummerOAuthClientStore(new InMemoryRegisteredClientRepository(client));
        clients.save(client);
        assertThat(clients.findByClientId("capability-client")).isEqualTo(client);
        assertThat(clients.findById(client.getId())).isEqualTo(client);
        SummerAuthorizationStore authorizations = new DefaultSummerAuthorizationStore(new InMemoryOAuth2AuthorizationService());
        var authorization = OAuth2Authorization.withRegisteredClient(client).principalName("capability-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).build();
        authorizations.save(authorization);
        assertThat(authorizations.findById(authorization.getId())).isEqualTo(authorization);
        authorizations.remove(authorization);
        assertThat(authorizations.findById(authorization.getId())).isNull();
        SummerConsentStore consents = new DefaultSummerConsentStore(new InMemoryOAuth2AuthorizationConsentService());
        var consent = OAuth2AuthorizationConsent.withId(client.getId(), "capability-user").scope("read").build();
        consents.save(consent);
        assertThat(consents.findById(client.getId(), "capability-user")).isEqualTo(consent);
        consents.remove(consent);
        assertThat(consents.findById(client.getId(), "capability-user")).isNull();
    }

    @Test
    void keyAdapterSelectsOnlyTheRequestedKey() throws Exception {
        var key = new OctetSequenceKey.Builder(new byte[32]).keyID("capability-key").build();
        SummerKeyStore keys = new DefaultSummerKeyStore(new JWKSet(key));
        assertThat(keys.get(new JWKSelector(new JWKMatcher.Builder().keyID("capability-key").build()), null))
                .containsExactly(key);
        assertThat(keys.get(new JWKSelector(new JWKMatcher.Builder().keyID("unknown").build()), null)).isEmpty();
    }

    @Test
    void servletSessionAdapterStoresAndRevokesTheNativeSecurityContext() {
        SummerSessionStore sessions = new DefaultSummerSessionStore();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("capability-user", null, List.of()));
        sessions.saveContext(context, request, response);
        assertThat(sessions.loadDeferredContext(request).get().getAuthentication().getName()).isEqualTo("capability-user");
        sessions.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        assertThat(sessions.loadDeferredContext(request).get().getAuthentication()).isNull();
    }
}
