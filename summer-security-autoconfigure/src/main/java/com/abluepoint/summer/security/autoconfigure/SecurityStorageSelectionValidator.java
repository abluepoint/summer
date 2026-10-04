package com.abluepoint.summer.security.autoconfigure;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.Locale;

/** Validates configured storage selection before the authorization server starts. */
final class SecurityStorageSelectionValidator {

    SecurityStorageSelectionValidator(
            SummerSecurityProperties properties,
            ObjectProvider<UserDetailsService> identityStores,
            ObjectProvider<RegisteredClientRepository> oauthClientStores,
            ObjectProvider<OAuth2AuthorizationService> authorizationStores,
            ObjectProvider<OAuth2AuthorizationConsentService> consentStores,
            ObjectProvider<SecurityContextRepository> sessionStores,
            ObjectProvider<JWKSource<SecurityContext>> keyStores
    ) {
        SummerSecurityProperties.Storage storage = properties.getStorage();
        require("identity", storage.getIdentity(), identityStores.getIfAvailable());
        require("oauth-client", storage.getOauthClient(), oauthClientStores.getIfAvailable());
        require("authorization", storage.getAuthorization(), authorizationStores.getIfAvailable());
        require("consent", storage.getConsent(), consentStores.getIfAvailable());
        require("session", storage.getSession(), sessionStores.getIfAvailable());
        require("keys", storage.getKeys(), keyStores.getIfAvailable());
    }

    private static void require(String name, String configured, Object selected) {
        String implementation = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        if (implementation.isBlank()) {
            throw new IllegalStateException("Storage implementation for " + name + " must not be blank");
        }
        if (!implementation.equals("memory") && selected == null) {
            throw new IllegalStateException("A " + implementation + " " + name
                    + " storage adapter bean is required");
        }
    }
}
