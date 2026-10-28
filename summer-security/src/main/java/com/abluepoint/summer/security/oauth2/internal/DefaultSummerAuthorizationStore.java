package com.abluepoint.summer.security.oauth2.internal;

import com.abluepoint.summer.security.oauth2.spi.SummerAuthorizationStore;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Objects;

/** Delegating authorization record store used by the default in-memory adapter. */
public final class DefaultSummerAuthorizationStore implements SummerAuthorizationStore {

    private final OAuth2AuthorizationService delegate;

    public DefaultSummerAuthorizationStore(OAuth2AuthorizationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
