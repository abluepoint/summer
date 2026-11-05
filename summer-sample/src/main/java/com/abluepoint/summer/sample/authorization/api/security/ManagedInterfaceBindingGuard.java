package com.abluepoint.summer.sample.authorization.api.security;

import com.abluepoint.summer.sample.resource.ManagedInterfaceRequestGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Revalidates managed identities; the resource module retains all other legacy/protocol guards. */
@Component
final class ManagedInterfaceBindingGuard implements ManagedInterfaceRequestGuard {
  private final ManagedRequestAuthorization authorization;

  ManagedInterfaceBindingGuard(ManagedRequestAuthorization authorization) {
    this.authorization = authorization;
  }

  @Override
  public boolean verify(HttpServletRequest request, Object handler) {
    try {
      return authorization.finalResource(
          request, handler, SecurityContextHolder.getContext().getAuthentication());
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException unavailable) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "The authoritative authorization store is unavailable");
    }
  }
}
