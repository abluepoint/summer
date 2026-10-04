package com.abluepoint.summer.security.autoconfigure.storage;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Stable signing-key/JWK selection boundary. Implementations must never expose
 * private key material through an application HTTP response.
 */
public interface SummerKeyStore extends JWKSource<SecurityContext> {
}
