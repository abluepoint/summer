package com.abluepoint.summer.security.authorization;

import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Optional resource-chain adapter. Protocol chains retain their own authorization policies. */
public interface ResourceRequestAuthorization extends AuthorizationManager<RequestAuthorizationContext> {}
