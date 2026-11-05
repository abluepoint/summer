package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

/** Server-installed non-MVC adapter. Metadata-only contributors cannot authorize requests. */
public interface InterfaceRuntimeAdapter {
  List<InterfaceMapping> mappings();

  /** Match the actual framework-selected handler and its complete predicates. */
  Optional<Match> match(HttpServletRequest request, Object handler);

  record Match(InterfaceMapping mapping, Domain domain) {}
}
