package com.abluepoint.summer.sample.resource;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Method guards preserve their explicit legacy branch and delegate managed business decisions. */
@Component("businessMethodAccess")
public class BusinessMethodAccess {
  private final BusinessResourceAccess resources;
  public BusinessMethodAccess(BusinessResourceAccess resources) { this.resources = resources; }
  public boolean require(Authentication actor, String... scopes) { return resources.requireCurrent(actor, scopes); }

  /** Method security runs before a controller's native denial adapter. */
  public boolean requirePreservingDenial(Authentication actor, String message, String... scopes) {
    try { return resources.requireCurrent(actor, scopes); }
    catch (org.springframework.security.access.AccessDeniedException denied) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.common.error.CommonErrorCode.FORBIDDEN, message, denied);
    }
  }
}
