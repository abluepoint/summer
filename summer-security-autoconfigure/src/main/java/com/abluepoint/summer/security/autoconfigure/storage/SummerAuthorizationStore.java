package com.abluepoint.summer.security.autoconfigure.storage;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

/**
 * Stable OAuth authorization/code/token record boundary.
 */
public interface SummerAuthorizationStore extends OAuth2AuthorizationService {
}
