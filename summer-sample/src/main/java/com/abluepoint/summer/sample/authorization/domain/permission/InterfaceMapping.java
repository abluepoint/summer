package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Trusted runtime registration, containing neither permission codes nor access modes. */
public record InterfaceMapping(
    String application,
    String source,
    String method,
    String path,
    Map<String, List<String>> conditions,
    String handler,
    String domainKind,
    boolean configurable) {
  public InterfaceMapping {
    for (String value : List.of(application, source, method, path, handler, domainKind))
      if (value.isBlank())
        throw new IllegalArgumentException("Complete interface mapping required");
    method = method.toUpperCase(Locale.ROOT);
    // Preserve framework path semantics, including case, regex and trailing slash.
    if (!path.startsWith("/"))
      throw new IllegalArgumentException("Absolute route template required");
    Map<String, List<String>> ordered = new TreeMap<>();
    conditions.forEach(
        (key, values) -> ordered.put(key, values.stream().distinct().sorted().toList()));
    conditions = Collections.unmodifiableMap(ordered);
    if (!List.of("PLATFORM", "ENTERPRISE", "COMPONENT").contains(domainKind))
      throw new IllegalArgumentException("Explicit interface domain boundary required");
  }
}
