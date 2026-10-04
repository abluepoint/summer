package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.core.env.Environment;

import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.KeyFactory;
import java.util.List;
import java.util.Map;

final class ReferenceSummerKeyStore implements SummerKeyStore {
    private final String source;
    private final String kid;
    private final JWKSet publicAndPrivate;

    ReferenceSummerKeyStore(Environment environment, String source, String name,
                            String allowedDirectory, String configuredKid) {
        var resolved = ReferenceKeyMaterialResolver.resolve(environment, source, name, allowedDirectory, configuredKid);
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(resolved.privateKey().getModulus(), resolved.privateKey().getPublicExponent()));
            this.source = resolved.source();
            this.kid = resolved.kid();
            this.publicAndPrivate = new JWKSet(new RSAKey.Builder(publicKey)
                    .privateKey(resolved.privateKey()).keyID(kid).algorithm(new com.nimbusds.jose.Algorithm("RS256")).build());
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_REFERENCE_FAILED", exception);
        }
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        return selector.select(publicAndPrivate);
    }

    List<Map<String, Object>> metadata() {
        return List.of(Map.of("kid", kid, "algorithm", "RS256", "source", source,
                "status", "ACTIVE", "version", 1, "health", "READY", "exists", true));
    }
}
