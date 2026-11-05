package com.abluepoint.summer.sample.resource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A trusted authorization bridge must revalidate the final handler before replacing legacy guards.
 */
public interface ManagedInterfaceRequestGuard {
  boolean verify(HttpServletRequest request, Object handler);
}
