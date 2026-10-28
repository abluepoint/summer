package com.abluepoint.summer.security.oauth2.internal;

import com.abluepoint.summer.security.oauth2.spi.SummerOAuthClientStore;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Objects;

/** Delegating registered-client store used by the default in-memory adapter. */
public final class DefaultSummerOAuthClientStore implements SummerOAuthClientStore {

    private final RegisteredClientRepository delegate;

    public DefaultSummerOAuthClientStore(RegisteredClientRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return delegate.findByClientId(clientId);
    }
}
