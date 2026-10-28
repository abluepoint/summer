package com.abluepoint.summer.security.oauth2.internal;

import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

import java.util.Objects;

/** Delegating authorization-consent store used by the default in-memory adapter. */
public final class DefaultSummerConsentStore implements SummerConsentStore {

    private final OAuth2AuthorizationConsentService delegate;

    public DefaultSummerConsentStore(OAuth2AuthorizationConsentService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void save(OAuth2AuthorizationConsent consent) {
        delegate.save(consent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent consent) {
        delegate.remove(consent);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return delegate.findById(registeredClientId, principalName);
    }
}
