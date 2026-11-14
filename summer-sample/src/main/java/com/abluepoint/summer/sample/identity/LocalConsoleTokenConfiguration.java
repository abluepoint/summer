package com.abluepoint.summer.sample.identity;

import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
class LocalConsoleTokenConfiguration {
    private static final List<String> BUSINESS_CLAIMS = List.of("enterprise_memberships", "enterprise_capabilities",
            "platform_capabilities", "organization_id", "active_enterprise_id", "organization_context",
            "release_operator", "summer_identity_source", "summer_managed_grants", "principal_type");

    @Bean OAuth2TokenCustomizer<JwtEncodingContext> localConsoleTokenCustomizer(LocalConsoleIdentityClaims identities) {
        return context -> {
            if (context.getTokenType() == null) return;
            if ("id_token".equals(context.getTokenType().getValue())) {
                context.getClaims().claim("preferred_username", context.getPrincipal().getName())
                        .claim("name", context.getPrincipal().getName()).expiresAt(Instant.now().plusSeconds(900));
            }
            if (!"access_token".equals(context.getTokenType().getValue())
                    || !"summer-ui".equals(context.getRegisteredClient().getClientId())
                    || !(context.getPrincipal() instanceof JwtAuthenticationToken login)
                    || !"local-user".equals(login.getToken().getClaimAsString("summer_identity_source"))) return;
            var claims = identities.resolve(login.getName(), login.getToken().getClaims());
            for (String key : BUSINESS_CLAIMS) if (claims.containsKey(key)) context.getClaims().claim(key, claims.get(key));
            // OAuth scope remains the client's authorized scopes; grants are read from current resources.
        };
    }

    @Bean JwtAuthenticationConverter localConsoleJwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());
        return converter;
    }
}
