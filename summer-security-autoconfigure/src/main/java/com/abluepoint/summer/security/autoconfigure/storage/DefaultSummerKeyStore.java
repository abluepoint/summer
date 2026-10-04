package com.abluepoint.summer.security.autoconfigure.storage;

import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.SecurityContext;

import java.util.List;
import java.util.Objects;

/** Delegating fixed JWK-set store used by the default development adapter. */
public final class DefaultSummerKeyStore implements SummerKeyStore {

    private final JWKSet jwkSet;

    public DefaultSummerKeyStore(JWKSet jwkSet) {
        this.jwkSet = Objects.requireNonNull(jwkSet, "jwkSet");
    }

    @Override
    public List<com.nimbusds.jose.jwk.JWK> get(JWKSelector selector, SecurityContext context)
            throws KeySourceException {
        return selector.select(jwkSet);
    }
}
