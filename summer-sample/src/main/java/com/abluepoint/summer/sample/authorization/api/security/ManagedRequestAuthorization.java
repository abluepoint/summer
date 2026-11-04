package com.abluepoint.summer.sample.authorization.api.security;

import com.abluepoint.summer.sample.authorization.*;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.security.authorization.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Resolve actual MVC conditions before authorization, then revalidate the final MVC handler. */
@Component
@org.springframework.context.annotation.Fallback
public class ManagedRequestAuthorization implements ResourceRequestAuthorization {
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final ManagedPermissionAccess access;
  private final UnifiedAuthorizationService unified;
  private final LegacyResourceAdapter resources;

  ManagedRequestAuthorization(
      ObjectProvider<RequestMappingHandlerMapping> mappings,
      ManagedPermissionAccess access,
      UnifiedAuthorizationService unified,
      LegacyResourceAdapter resources) {
    this.mappings = mappings;
    this.access = access;
    this.unified = unified;
    this.resources = resources;
  }

  public static ResourceAuthorization.Domain domain(HttpServletRequest r) {
    Object v = r.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (v instanceof Map<?, ?> vars && vars.get("enterpriseId") instanceof String id)
      return ResourceAuthorization.Domain.enterprise(id);
    Object p = r.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (p != null && p.toString().startsWith("/api/v1/platform/"))
      return ResourceAuthorization.Domain.platform();
    return null;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    var r = context.getRequest();
    var a = authentication.get();
    try {
      for (var mapping : mappings.orderedStream().toList()) {
        var chain = mapping.getHandler(r);
        if (chain == null || !(chain.getHandler() instanceof HandlerMethod handler)) continue;
        var d = domain(r);
        if (d != null && access.managed(d))
          return new AuthorizationDecision(evaluate(r, handler, a, d));
      }
      return new AuthorizationDecision(access.authenticated(a));
    } catch (org.springframework.web.HttpRequestMethodNotSupportedException
        | org.springframework.web.HttpMediaTypeException
        | org.springframework.web.bind.ServletRequestBindingException protocolMappingFailure) {
      // No business handler matched. Let DispatcherServlet preserve native 400/405/406/415
      // semantics.
      return new AuthorizationDecision(access.authenticated(a));
    } catch (org.springframework.security.access.AccessDeniedException denied) {
      return new AuthorizationDecision(false);
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException unavailable) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "The authoritative authorization store is unavailable");
    } catch (RuntimeException unavailable) {
      throw unavailable;
    } catch (Exception unavailable) {
      throw new IllegalStateException("Cannot resolve authoritative resource", unavailable);
    }
  }

  public boolean finalHandler(HttpServletRequest r, HandlerMethod handler, Authentication a) {
    var d = domain(r);
    if (d == null || !access.managed(d)) return false;
    if (!evaluate(r, handler, a, d))
      throw new org.springframework.security.access.AccessDeniedException(
          "Current permission required");
    return true;
  }

  private boolean evaluate(
      HttpServletRequest r,
      HandlerMethod handler,
      Authentication a,
      ResourceAuthorization.Domain d) {
    var resource = resources.resolve(r, handler);
    r.setAttribute(ManagedPermissionAccess.RESOURCE_ATTRIBUTE, resource.id());
    // These scopes belong only to the management/representative surface installed in slice 03.
    // Other business handlers retain their own method/service delegation boundaries.
    boolean management =
        java.util.Set.of(
                com.abluepoint.summer.sample.authorization.api.permission.PermissionController
                    .class,
                com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController
                    .class,
                com.abluepoint.summer.sample.authorization.api.permission.PermissionAccessController
                    .class)
            .contains(handler.getBeanType());
    if (management
        && !access.delegated(
            d, a, !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod())))
      return false;
    boolean allowed =
        unified
            .decide(new ResourceAuthorization.Request(d, a, resource, r.getHeader("X-Request-Id")))
            .allowed();
    if (allowed
        && (handler
                .getBeanType()
                .equals(
                    com.abluepoint.summer.sample.authorization.api.permission.PermissionController
                        .class)
            || handler
                .getBeanType()
                .equals(
                    com.abluepoint.summer.sample.authorization.api.permission
                        .RolePermissionController.class)))
      access.requireManagement(
          d, a, !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod()));
    return allowed;
  }
}
