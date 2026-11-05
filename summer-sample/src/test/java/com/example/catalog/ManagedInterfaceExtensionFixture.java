package com.example.catalog;

import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.InterfaceRuntimeAdapter;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

@TestConfiguration
public class ManagedInterfaceExtensionFixture {
  private final HttpRequestHandler handler =
      (request, response) -> {
        response.setContentType("application/json");
        response.getWriter().write("{\"extension\":true}");
      };
  private final InterfaceMapping mapping =
      new InterfaceMapping(
          "application",
          "TEST_URL",
          "GET",
          "/api/v1/platform/interface-extension",
          Map.of(),
          "explicit-extension-handler-v1",
          "PLATFORM",
          true);

  private final InterfaceMapping overlap =
      new InterfaceMapping(
          "application",
          "TEST_URL",
          "GET",
          "/api/v1/platform/interface-overlap",
          Map.of(),
          "explicit-overlap-handler-v1",
          "PLATFORM",
          true);

  @Bean
  SimpleUrlHandlerMapping managedExtensionMapping() {
    return new SimpleUrlHandlerMapping(
        Map.of(mapping.path(), handler, overlap.path(), handler), -1);
  }

  @Bean
  InterfaceRuntimeAdapter managedExtensionAdapter() {
    return new InterfaceRuntimeAdapter() {
      @Override
      public List<InterfaceMapping> mappings() {
        return List.of(mapping, overlap);
      }

      @Override
      public Optional<Match> match(jakarta.servlet.http.HttpServletRequest request, Object actual) {
        if (actual != handler || !request.getMethod().equals("GET")) return Optional.empty();
        return mappings().stream()
            .filter(candidate -> request.getRequestURI().equals(candidate.path()))
            .map(candidate -> new Match(candidate, Domain.platform()))
            .findFirst();
      }
    };
  }
}
