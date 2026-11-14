package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.sample.authorization.UnifiedAuthorizationService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Service gates reuse the actual installed HTTP resource, never a legacy business code. */
@Service
public class ManagedConsumerAccess
    implements com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization {
  public static final String DOMAIN_ATTRIBUTE = ManagedConsumerAccess.class.getName() + ".domain";
  public static final String DECISION_RESOURCE_ATTRIBUTE =
      ManagedConsumerAccess.class.getName() + ".resource";
  private final ManagedPermissionAccess access;
  private final org.springframework.beans.factory.ObjectProvider<UnifiedAuthorizationService>
      authorization;
  private final org.springframework.beans.factory.ObjectProvider<InterfaceOperationService> operations;

  private final org.springframework.transaction.support.TransactionTemplate fresh;

  public ManagedConsumerAccess(
      ManagedPermissionAccess access,
      org.springframework.beans.factory.ObjectProvider<UnifiedAuthorizationService> authorization,
      org.springframework.beans.factory.ObjectProvider<InterfaceOperationService> operations,
      org.springframework.transaction.PlatformTransactionManager transactions) {
    this.access = access;
    this.authorization = authorization;
    this.operations = operations;
    this.fresh = new org.springframework.transaction.support.TransactionTemplate(transactions);
    fresh.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    fresh.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    fresh.setReadOnly(true);
  }

  /** Only current managed resources authorize target service calls. */
  public boolean require(Domain domain, Authentication actor, String... scopes) {
    try { return Boolean.TRUE.equals(fresh.execute(status -> current(domain, actor, scopes))); }
    catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
      throw unavailable();
    }
  }

  private boolean current(Domain domain, Authentication actor, String... scopes) {
    if (!access.managed(domain)) throw denied();
    if (!access.active(domain, actor)
        || !delegated(actor, scopes)
        || !(RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes context)) throw denied();
    var request = context.getRequest();
    if (!domain.equals(request.getAttribute(DOMAIN_ATTRIBUTE))
        || !actor.equals(SecurityContextHolder.getContext().getAuthentication())
        || !(request.getAttribute(DECISION_RESOURCE_ATTRIBUTE)
            instanceof ResourceAuthorization.Resource resource)
        || !authorization
            .getObject()
            .decide(
                new ResourceAuthorization.Request(
                    domain, actor, resource, com.abluepoint.summer.mvc.request.ServletRequestIdAccessor.requestId(request)))
            .allowed()
        || !access.roleAllows(domain, actor, resource.id())) throw denied();
    return true;
  }

  public boolean managed(Domain domain) {
    return access.managed(domain);
  }

  public boolean organizationWriteAllowed(Domain domain, Authentication actor) {
    try { return Boolean.TRUE.equals(fresh.execute(status -> {
      if (!domain.kind().equals("ENTERPRISE") || !access.managed(domain) || !access.authenticated(actor)
          || !actor.equals(SecurityContextHolder.getContext().getAuthentication())
          || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context))
        return false;
      return operations.getObject().qualify(domain, actor,
          java.util.List.of("organization.create", "organization.update", "organization.delete", "organization.assign"),
          com.abluepoint.summer.mvc.request.ServletRequestIdAccessor.requestId(context.getRequest()))
          .values().stream().anyMatch(Boolean.TRUE::equals);
    })); }
    catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
      throw unavailable();
    }
  }

  private static com.abluepoint.summer.common.error.SummerException unavailable() {
    return com.abluepoint.summer.common.error.SummerException.withMessage(
        com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
        "Current consumer authorization unavailable");
  }

  public static boolean delegated(Authentication actor, String... scopes) {
    if (!(actor instanceof JwtAuthenticationToken jwt)
        || "local-console-session".equals(jwt.getToken().getTokenValue())
        || scopes.length == 0) return true;
    Object raw = jwt.getToken().getClaims().get("scope");
    Collection<?> granted =
        raw instanceof Collection<?> values
            ? values
            : raw instanceof String value ? Arrays.asList(value.split(" ")) : java.util.List.of();
    return Arrays.stream(scopes).anyMatch(granted::contains);
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("Current management resource permission required");
  }
}
