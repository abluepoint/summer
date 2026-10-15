package com.abluepoint.summer.sample.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalConsoleTokenContextTest {
    @Test
    void copiesResolvedActiveTenantAndOrganizationWithoutChangingOAuthScope() {
        var identities = mock(LocalConsoleIdentityClaims.class);
        var organization = Map.of("enterpriseId", "tenant-selected", "organizationId", "org-selected");
        when(identities.resolve(eq("context-user"), anyMap())).thenReturn(Map.of(
                "active_enterprise_id", "tenant-selected", "organization_context", organization,
                "enterprise_memberships", List.of(Map.of("enterpriseId", "tenant-selected", "status", "ACTIVE")),
                "scope", List.of("untrusted-expanded-scope")));
        var context = context("summer-ui", "local-user");

        new LocalConsoleTokenConfiguration().localConsoleTokenCustomizer(identities).customize(context);

        var claims = context.getClaims().build().getClaims();
        assertThat(claims).containsEntry("active_enterprise_id", "tenant-selected")
                .containsEntry("organization_context", organization);
        assertThat(claims.get("scope")).isEqualTo(List.of("openid", "profile"));
    }

    @Test
    void doesNotReusePriorContextWhenResolverNoLongerReturnsIt() {
        var identities = mock(LocalConsoleIdentityClaims.class);
        when(identities.resolve(eq("context-user"), anyMap())).thenReturn(Map.of(
                "enterprise_memberships", List.of(Map.of("enterpriseId", "tenant-selected", "status", "ACTIVE"))));
        var context = context("summer-ui", "local-user");

        new LocalConsoleTokenConfiguration().localConsoleTokenCustomizer(identities).customize(context);

        assertThat(context.getClaims().build().getClaims()).doesNotContainKeys("active_enterprise_id", "organization_context");
    }

    @Test
    void doesNotCopyBusinessContextForAnotherClient() {
        var identities = mock(LocalConsoleIdentityClaims.class);
        var context = context("another-client", "local-user");
        new LocalConsoleTokenConfiguration().localConsoleTokenCustomizer(identities).customize(context);
        verifyNoInteractions(identities);
        assertThat(context.getClaims().build().getClaims()).doesNotContainKeys("active_enterprise_id", "organization_context");
    }

    @Test
    void doesNotCopyBusinessContextForAnotherIdentitySource() {
        var identities = mock(LocalConsoleIdentityClaims.class);
        var context = context("summer-ui", "external-user");
        new LocalConsoleTokenConfiguration().localConsoleTokenCustomizer(identities).customize(context);
        verifyNoInteractions(identities);
        assertThat(context.getClaims().build().getClaims()).doesNotContainKeys("active_enterprise_id", "organization_context");
    }

    private JwtEncodingContext context(String clientId, String identitySource) {
        var now = Instant.now();
        var login = Jwt.withTokenValue("in-memory-test-principal").header("alg", "none").subject("context-user")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("summer_identity_source", identitySource)
                .claim("active_enterprise_id", "prior-tenant")
                .claim("organization_context", Map.of("enterpriseId", "prior-tenant", "organizationId", "prior-org"))
                .build();
        var client = RegisteredClient.withId("context-client").clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1/callback").scope("openid").scope("profile").build();
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder()
                        .subject("context-user").claim("scope", List.of("openid", "profile")))
                .registeredClient(client).principal(new JwtAuthenticationToken(login))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
    }
}
