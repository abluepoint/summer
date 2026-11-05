package com.abluepoint.summer.sample.resource;

import java.util.List;
import java.util.Map;

/** Server-installed operations; callers supply the installed bean, never resource IDs or paths. */
public interface RuntimeBusinessResources {
  List<Operation> operations();

  record Operation(
      String key, String method, String path, String domainKind,
      Map<String, List<String>> conditions) {}
}
