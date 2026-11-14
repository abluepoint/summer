package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** Exact installed monitoring operations; metadata contributors never grant runtime access. */
@Component
public final class MonitoringInterfaceRuntimeAdapter implements InterfaceRuntimeAdapter {
  private static final Set<String> ACTUATOR = Set.of(
      "/actuator/metrics", "/actuator/metrics/{requiredMetricName}", "/actuator/prometheus");
  private static final String OPERATION_HANDLER =
      "org.springframework.boot.webmvc.actuate.endpoint.web.AbstractWebMvcEndpointHandlerMapping$OperationHandler";
  private final ObjectProvider<HandlerMapping> mappings;

  public MonitoringInterfaceRuntimeAdapter(ObjectProvider<HandlerMapping> mappings) {
    this.mappings = mappings;
  }

  private record Installed(RequestMappingInfo conditions, HandlerMethod handler, InterfaceMapping resource) {}

  private List<Installed> installed() {
    var result = new ArrayList<Installed>();
    for (var mapping : mappings.orderedStream().toList()) {
      if (!(mapping instanceof AbstractHandlerMethodMapping<?> methods)) continue;
      for (var entry : methods.getHandlerMethods().entrySet()) {
        if (!(entry.getKey() instanceof RequestMappingInfo info) || info.getCustomCondition() != null) continue;
        var handler = entry.getValue();
        for (String path : info.getPatternValues()) {
          boolean actuator = mapping instanceof WebMvcEndpointHandlerMapping
              && handler.getBeanType().getName().equals(OPERATION_HANDLER) && ACTUATOR.contains(path);
          boolean runtime = handler.getBeanType().getName().equals(
              "com.abluepoint.summer.sample.operations.RuntimeDiagnosticsController")
              && handler.getMethod().getName().equals("runtime")
              && path.equals("/internal/v1/diagnostics/runtime");
          boolean resilience = handler.getBeanType().getName().equals(
              "com.abluepoint.summer.sample.resilience.ResilienceDiagnosticsController")
              && handler.getMethod().getName().equals("snapshot")
              && path.equals("/internal/v1/diagnostics/resilience");
          if ((!actuator && !runtime && !resilience) || !info.getMethodsCondition().getMethods()
              .contains(org.springframework.web.bind.annotation.RequestMethod.GET)) continue;
          for (String method : List.of("GET", "HEAD")) {
            var mvc = ManagedInterfaceResources.mvc(method, path, handler, info);
            String identity = mvc.handler() + (actuator ? "|" + handler.getBean() : "");
            result.add(new Installed(info, handler, new InterfaceMapping(mvc.application(),
                "MONITORING", method, path, mvc.conditions(), identity, "PLATFORM", true)));
          }
        }
      }
    }
    return result;
  }

  @Override public List<InterfaceMapping> mappings() {
    return installed().stream().map(Installed::resource).toList();
  }

  @Override public Optional<Match> match(HttpServletRequest request, Object selected) {
    if (!(selected instanceof HandlerMethod handler)) return Optional.empty();
    var matches = installed().stream()
        .filter(candidate -> candidate.resource().method().equals(request.getMethod())
            && candidate.conditions().getMatchingCondition(request) != null
            && candidate.handler().createWithResolvedBean().equals(handler.createWithResolvedBean()))
        .toList();
    if (matches.size() > 1)
      throw new org.springframework.security.access.AccessDeniedException("Ambiguous installed monitoring operation");
    return matches.stream().findFirst().map(candidate -> new Match(candidate.resource(), Domain.platform()));
  }
}
