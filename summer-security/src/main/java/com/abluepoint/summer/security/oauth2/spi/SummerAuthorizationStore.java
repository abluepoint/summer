package com.abluepoint.summer.security.oauth2.spi;

import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

/**
 * Stable OAuth authorization/code/token record boundary.
 */
public interface SummerAuthorizationStore extends OAuth2AuthorizationService {
}
