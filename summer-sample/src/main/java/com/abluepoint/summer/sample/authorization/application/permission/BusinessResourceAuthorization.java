package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.BusinessRuntimeResources;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.sample.resource.BusinessResourceAccess;
import com.abluepoint.summer.sample.resource.RuntimeBusinessResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import com.abluepoint.summer.sample.authorization.AuthorizationDomainVersions;
import com.abluepoint.summer.mvc.request.ServletRequestIdAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Public, authenticated and role access all come from the same current resource decision. */
@Service
public class BusinessResourceAuthorization implements BusinessResourceAccess {
  private final AuthorizationDomainVersions versions;
  private final ObjectProvider<ResourceAuthorization> authorization;
  private final ObjectProvider<ManagedInterfaceResources> interfaces;
  private final BusinessRuntimeResources runtime;
  private final ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings;

  public BusinessResourceAuthorization(AuthorizationDomainVersions versions, ObjectProvider<ResourceAuthorization> authorization,
      ObjectProvider<ManagedInterfaceResources> interfaces, BusinessRuntimeResources runtime,
      ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings) {
    this.versions = versions;
    this.authorization = authorization;
    this.interfaces = interfaces;
    this.runtime = runtime;
    this.mappings = mappings;
  }

  private boolean legacy(Domain domain) {
    return !versions.managed(domain);
  }

  @Override public boolean requireCurrent(Authentication actor, String... delegatedScopes) {
    if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context)) throw denied();
    var request = context.getRequest();
    if (request.getAttribute(ManagedConsumerAccess.DOMAIN_ATTRIBUTE) instanceof Domain domain)
      return require(domain, actor, delegatedScopes);
    if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)) throw denied();
    var owner = handler.getBeanType().getName();
    var path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    String kind = com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerRoutes.domainKind(owner, path);
    if ("COMPONENT".equals(kind)) kind = com.abluepoint.summer.sample.authorization.domain.permission.BusinessConsumerRoutes.domainKind(owner, path);
    Domain domain = "PLATFORM".equals(kind) ? Domain.platform()
        : "ENTERPRISE".equals(kind) ? Domain.enterprise(com.abluepoint.summer.sample.authorization.EnterpriseRequestContext.resolve(actor)) : null;
    if (domain == null) throw denied();
    // Only LEGACY can arrive without the current managed request identity.
    if (legacy(domain)) return false;
    throw denied();
  }

  @Override public boolean require(Domain domain, Authentication actor, String... delegatedScopes) {
    return current(() -> {
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      if (legacy(domain)) return false;
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)
          || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context)
          || !(Objects.equals(actor, SecurityContextHolder.getContext().getAuthentication())
              || actor == null && SecurityContextHolder.getContext().getAuthentication()
                  instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) throw denied();
      var request = context.getRequest();
      if (!domain.equals(request.getAttribute(ManagedConsumerAccess.DOMAIN_ATTRIBUTE))
          || !(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)) throw denied();
      var resource = interfaces.getObject().actual(request, handler.createWithResolvedBean());
      if (!resource.equals(request.getAttribute(ManagedConsumerAccess.DECISION_RESOURCE_ATTRIBUTE))) throw denied();
      decide(domain, actor, resource, ServletRequestIdAccessor.requestId(request));
      return true;
    });
  }

  @Override public boolean requireRuntime(Domain domain, Authentication actor,
      RuntimeBusinessResources installed, String operation, String... delegatedScopes) {
    return current(() -> {
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      if (legacy(domain)) return false;
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      var resource = interfaces.getObject().registered(runtime.resolve(installed, operation, domain));
      decide(domain, actor, resource, null);
      return true;
    });
  }

  @Override public boolean requirePublicConfigurationRead(Domain domain, Authentication actor,
      String resource, String... delegatedScopes) {
    if (!java.util.Set.of("dictionaries", "system-parameters").contains(resource)) throw denied();
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context)
        || !(context.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
            instanceof HandlerMethod handler)
        || !"system-parameters".equals(resource)
        || !handler.getBeanType().getName().equals(
            "com.abluepoint.summer.sample.systemparameter.PublicSystemParameterController"))
      return require(domain, actor, delegatedScopes);
    return current(() -> {
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      if (legacy(domain)) return false;
      var request = context.getRequest();
      String path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
      if (!"ENTERPRISE".equals(domain.kind()) || !"GET".equals(request.getMethod())
          || !java.util.Set.of("/public/system-parameters/{key}",
              "/public/enterprises/{enterpriseId}/system-parameters/{key}").contains(path)
          || !domain.id().equals(com.abluepoint.summer.sample.authorization.EnterpriseRequestContext.resolve(actor))
          || !(Objects.equals(actor, SecurityContextHolder.getContext().getAuthentication())
              || actor == null && SecurityContextHolder.getContext().getAuthentication()
                  instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) throw denied();
      var installed = mappings.orderedStream().flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
          .filter(entry -> entry.getKey().getPatternValues().contains(path)
              && entry.getKey().getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.GET)
              && entry.getKey().getMatchingCondition(request) != null
              && entry.getValue().createWithResolvedBean().equals(handler.createWithResolvedBean())).toList();
      if (installed.size() != 1 || installed.getFirst().getKey().getCustomCondition() != null) throw denied();
      var mapping = ManagedInterfaceResources.mvc("GET", path,
          installed.getFirst().getValue(), installed.getFirst().getKey());
      if (mapping.configurable() || !"COMPONENT".equals(mapping.domainKind())) throw denied();
      // This fixed transport grants no interface access. Its caller still executes the current
      // DATA predicate AND the original published/public/status predicates before returning a value.
      return true;
    });
  }

  @Override public boolean requireHttp(Domain domain, Authentication actor, String method, String path,
      String... delegatedScopes) {
    return current(() -> {
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      if (legacy(domain)) return false;
      if (!ManagedConsumerAccess.delegated(actor, delegatedScopes)) throw denied();
      var candidates = mappings.orderedStream().flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
          .filter(entry -> entry.getKey().getPatternValues().contains(path)
              && entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(value -> value.name().equals(method)))
          .toList();
      if (candidates.size() != 1 || candidates.getFirst().getKey().getCustomCondition() != null) throw denied();
      var entry = candidates.getFirst();
      var mapping = ManagedInterfaceResources.mvc(method, path, entry.getValue(), entry.getKey());
      if (!mapping.configurable() || !mapping.domainKind().equals(domain.kind())) throw denied();
      decide(domain, actor, interfaces.getObject().registered(mapping), null);
      return true;
    });
  }

  @Override public <T> T readManagedHttp(Domain domain,Authentication actor,String method,String path,
      java.util.function.Supplier<T> query,String... scopes) {
    return versions.readManaged(domain,revision -> {
      if (!requireHttp(domain,actor,method,path,scopes)) throw denied();
      return query.get();
    });
  }

  private void decide(Domain domain, Authentication actor, ResourceAuthorization.Resource resource, String requestId) {
    if (!authorization.getObject().decide(new ResourceAuthorization.Request(domain, actor, resource, requestId)).allowed()) throw denied();
  }

  private boolean current(java.util.function.BooleanSupplier invocation) {
    try { return invocation.getAsBoolean(); }
    catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
      throw SummerException.withMessage(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "Current business authorization unavailable", failure);
    }
  }

  private static AccessDeniedException denied() {
    return new AccessDeniedException("Current business resource permission required");
  }
}
