package com.abluepoint.summer.security.oauth2.spi;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Stable registered OAuth client lookup boundary.
 */
public interface SummerOAuthClientStore extends RegisteredClientRepository {
}
