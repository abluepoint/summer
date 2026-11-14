package com.abluepoint.summer.sample.testsupport;

import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.InterfaceRuntimeAdapter;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Explicit registration of test-only controller owners, using the normal current authorization. */
@TestConfiguration(proxyBeanMethods = false)
public class ExactCurrentHttpTestFixture {
  public record Owner(Class<?> controller) {
    public Owner {
      if (controller == null || controller.getEnclosingClass() == null
          || !controller.getEnclosingClass().getSimpleName().endsWith("Test"))
        throw new IllegalArgumentException("An exact nested test controller is required");
    }
  }

  @Bean
  InterfaceRuntimeAdapter exactCurrentTestInterfaces(
      ObjectProvider<RequestMappingHandlerMapping> mappings, ObjectProvider<Owner> owners) {
    return new InterfaceRuntimeAdapter() {
      record Installed(RequestMappingInfo conditions, HandlerMethod handler, InterfaceMapping resource) {}

      private List<Installed> installed() {
        var accepted = owners.stream().map(Owner::controller).toList();
        var result = new ArrayList<Installed>();
        for (var mapping : mappings.orderedStream().toList())
          for (var entry : mapping.getHandlerMethods().entrySet()) {
            if (!accepted.contains(entry.getValue().getBeanType())) continue;
            if (!(entry.getValue().getBean() instanceof String))
              throw new IllegalStateException("A named installed test controller bean is required");
            var info = entry.getKey();
            if (info.getCustomCondition() != null || info.getMethodsCondition().getMethods().isEmpty())
              throw new IllegalStateException("Complete test request mapping required");
            for (String path : info.getPatternValues())
              for (var method : info.getMethodsCondition().getMethods()) {
                var methods = method.name().equals("GET") ? List.of("GET", "HEAD") : List.of(method.name());
                for (String verb : methods) {
                  var mvc = ManagedInterfaceResources.mvc(verb, path, entry.getValue(), info);
                  result.add(new Installed(info, entry.getValue(), new InterfaceMapping(
                      mvc.application(), "TEST_HTTP", verb, path, mvc.conditions(), mvc.handler(), "PLATFORM", true)));
                }
              }
          }
        return result.stream().distinct().toList();
      }

      @Override public List<InterfaceMapping> mappings() {
        return installed().stream().map(Installed::resource).toList();
      }

      @Override public Optional<Match> match(HttpServletRequest request, Object selected) {
        if (!(selected instanceof HandlerMethod handler)) return Optional.empty();
        var found = installed().stream()
            .filter(candidate -> candidate.resource().method().equals(request.getMethod())
                && candidate.conditions().getMatchingCondition(request) != null
                && candidate.conditions().getMatchingCondition(request).getPatternValues()
                    .contains(candidate.resource().path())
                && candidate.handler().createWithResolvedBean().equals(handler.createWithResolvedBean()))
            .toList();
        if (found.size() > 1) throw new IllegalStateException("Ambiguous exact test interface");
        return found.stream().findFirst().map(candidate -> new Match(candidate.resource(), Domain.platform()));
      }
    };
  }
}
