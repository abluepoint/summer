package com.abluepoint.summer.sample.authorization.api.security;

import com.abluepoint.summer.sample.authorization.*;
import com.abluepoint.summer.sample.authorization.api.permission.InterfacePermissionController;
import com.abluepoint.summer.sample.authorization.api.permission.MenuDirectoryController;
import com.abluepoint.summer.sample.authorization.api.permission.MenuPermissionController;
import com.abluepoint.summer.sample.authorization.api.permission.PermissionAccessController;
import com.abluepoint.summer.sample.authorization.api.permission.PermissionController;
import com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController;
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

/** Resolve actual MVC conditions before authorization, then revalidate the final MVC handler. */
@Component
@org.springframework.context.annotation.Fallback
public class ManagedRequestAuthorization implements ResourceRequestAuthorization {
  private final ManagedPermissionAccess access;
  private final UnifiedAuthorizationService unified;
  private final com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources resources;
  private final ObjectProvider<HandlerMapping> allMappings;
  private static final String MATCH_ATTRIBUTE = ManagedRequestAuthorization.class.getName()+".matchedResource";

  ManagedRequestAuthorization(
      ManagedPermissionAccess access,
      UnifiedAuthorizationService unified,
      com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources resources,
      ObjectProvider<HandlerMapping> allMappings) {
    this.access = access;
    this.unified = unified;
    this.resources = resources;
    this.allMappings = allMappings;
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
      // DispatcherServlet uses the first matching chain across every HandlerMapping type.
      for (var mapping : allMappings.orderedStream().toList()) {
        var chain = mapping.getHandler(r);
        if (chain == null) continue;
        if (chain.getHandler() instanceof HandlerMethod handler) {
          var d = domain(r);
          return new AuthorizationDecision(
              d != null && access.managed(d)
                  ? evaluate(r, handler, a, d)
                  : access.authenticated(a));
        }
        // This installed transport retains its native JWT, scope, tenant and cursor handshake.
        if (realtimeHandshake(r, mapping, chain.getHandler()))
          return new AuthorizationDecision(access.authenticated(a));
        var match = resources.extension(r, chain.getHandler());
        if (match.isPresent() && access.managed(match.get().domain())) {
          var resource = resources.registered(match.get().mapping());
          r.setAttribute(MATCH_ATTRIBUTE, resource.id());
          var decision=unified.decide(new ResourceAuthorization.Request(
              match.get().domain(), a, resource, r.getHeader("X-Request-Id")));
          return new AuthorizationDecision(decision.allowed());
        }
        // Unknown business extension mappings require explicit runtime adapters.
        if (!(chain.getHandler() instanceof org.springframework.web.servlet.resource.ResourceHttpRequestHandler))
          return new AuthorizationDecision(false);
        return new AuthorizationDecision(access.authenticated(a));
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

  private static boolean realtimeHandshake(HttpServletRequest request, HandlerMapping mapping, Object handler) {
    // URL mapping attributes are exposed by an interceptor only after Security has run.
    // Require this exact registration and raw route instead of trusting a stale MVC attribute.
    if (!(mapping instanceof org.springframework.web.servlet.handler.AbstractUrlHandlerMapping urls)
        || !(handler instanceof org.springframework.web.socket.server.support.WebSocketHttpRequestHandler socket)
        || urls.getHandlerMap().get("/ws/v1/messages") != handler
        || !(request.getContextPath() + "/ws/v1/messages").equals(request.getRequestURI()))
      return false;
    var delegate = org.springframework.web.socket.handler.WebSocketHandlerDecorator.unwrap(socket.getWebSocketHandler());
    return org.springframework.util.ClassUtils.getUserClass(delegate).getName()
            .equals("com.abluepoint.summer.sample.realtime.RealtimeWebSocketHandler")
        && socket.getHandshakeInterceptors().stream().anyMatch(interceptor ->
            org.springframework.util.ClassUtils.getUserClass(interceptor).getName()
                .equals("com.abluepoint.summer.sample.realtime.RealtimeHandshakeInterceptor"));
  }

  public boolean finalHandler(HttpServletRequest r, HandlerMethod handler, Authentication a) {
    try {
    var d = domain(r);
    if (d == null || !access.managed(d)) return false;
    if (applicant(r,handler)) return false;
    if (!evaluate(r, handler, a, d))
      throw new org.springframework.security.access.AccessDeniedException(
          "Current permission required");
    return true;
    } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "The authoritative authorization store is unavailable");
    }
  }

  private static boolean applicant(HttpServletRequest request,HandlerMethod handler) {
    return com.abluepoint.summer.sample.authorization.domain.permission.InterfaceComponentPolicy.applicant(
        handler.getBeanType().getName(),request.getMethod(),
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)));
  }

  public void finalExtension(HttpServletRequest request, Object handler, Authentication actor) {
    if (handler instanceof HandlerMethod) return;
    var matched = resources.extension(request, handler);
    if (matched.isEmpty()) {
      if (request.getAttribute(MATCH_ATTRIBUTE) != null)
        throw new org.springframework.security.access.AccessDeniedException("Final extension adapter changed");
      return;
    }
    var match = matched.get();
    if (!access.managed(match.domain())) return;
    var resource = resources.registered(match.mapping());
    if (!resource.id().equals(request.getAttribute(MATCH_ATTRIBUTE))
        || !unified.decide(new ResourceAuthorization.Request(match.domain(), actor, resource,
            request.getHeader("X-Request-Id"))).allowed())
      throw new org.springframework.security.access.AccessDeniedException("Current extension permission required");
  }

  public boolean finalResource(HttpServletRequest request,Object handler,Authentication actor) {
    if (handler instanceof HandlerMethod method) return finalHandler(request,method,actor);
    var match=resources.extension(request,handler);
    if(match.isEmpty() || !access.managed(match.get().domain())) return false;
    finalExtension(request,handler,actor);
    return true;
  }

  private boolean evaluate(
      HttpServletRequest r,
      HandlerMethod handler,
      Authentication a,
      ResourceAuthorization.Domain d) {
    // Applicant operations keep their exact scope/subject/state checks in the existing interceptor.
    if (applicant(r,handler)) return access.authenticated(a);
    var resource = resources.actual(r, handler);
    Object previous = r.getAttribute(MATCH_ATTRIBUTE);
    if (previous != null && !previous.equals(resource.id()))
      throw new org.springframework.security.access.AccessDeniedException("Final handler identity changed");
    r.setAttribute(MATCH_ATTRIBUTE, resource.id());
    r.setAttribute(ManagedPermissionAccess.RESOURCE_ATTRIBUTE, resource.id());
    // These scopes belong only to the management/representative surface installed in slice 03.
    // Other business handlers retain their own method/service delegation boundaries.
    boolean management = java.util.Set.of(PermissionController.class,
        InterfacePermissionController.class, MenuPermissionController.class,
        MenuDirectoryController.class, RolePermissionController.class,
        PermissionAccessController.class).contains(handler.getBeanType());
    if (management
        && !access.delegated(
            d, a, !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod())))
      return false;
    boolean allowed =
        unified
            .decide(new ResourceAuthorization.Request(d, a, resource, r.getHeader("X-Request-Id")))
            .allowed();
    if (allowed && management && !handler.getBeanType().equals(PermissionAccessController.class))
      access.requireManagement(
          d, a, !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod()));
    return allowed;
  }
}
