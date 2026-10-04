package com.abluepoint.summer.security.autoconfigure.storage;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Stable registered OAuth client lookup boundary.
 */
public interface SummerOAuthClientStore extends RegisteredClientRepository {
}
