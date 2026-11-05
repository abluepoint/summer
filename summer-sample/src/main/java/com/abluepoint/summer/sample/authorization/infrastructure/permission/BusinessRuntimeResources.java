package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.sample.resource.RuntimeBusinessResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Uses the interface catalog's identities and lifecycle for continuous business operations. */
@Component
public class BusinessRuntimeResources implements InterfaceRuntimeAdapter {
  private final ListableBeanFactory installed;

  public BusinessRuntimeResources(ListableBeanFactory installed) {
    this.installed = installed;
  }

  private InterfaceMapping mapping(String beanName, RuntimeBusinessResources owner, RuntimeBusinessResources.Operation op) {
    return new InterfaceMapping("application", "RUNTIME", op.method(), op.path(),
        op.conditions(), org.springframework.util.ClassUtils.getUserClass(owner).getName() + "@" + beanName + "#" + op.key(),
        op.domainKind(), true);
  }

  @Override public List<InterfaceMapping> mappings() {
    return installed.getBeansOfType(RuntimeBusinessResources.class).entrySet().stream()
        .flatMap(entry -> entry.getValue().operations().stream()
        .map(op -> mapping(entry.getKey(), entry.getValue(), op))).toList();
  }

  public InterfaceMapping resolve(RuntimeBusinessResources owner, String operation, Domain domain) {
    var owners = installed.getBeansOfType(RuntimeBusinessResources.class).entrySet().stream()
        .filter(entry -> entry.getValue() == owner).toList();
    if (owners.size() != 1)
      throw new AccessDeniedException("Installed runtime owner required");
    var matches = owner.operations().stream().filter(op -> op.key().equals(operation)
        && op.domainKind().equals(domain.kind())).toList();
    if (matches.size() != 1) throw new AccessDeniedException("Unique runtime operation required");
    return mapping(owners.getFirst().getKey(), owner, matches.getFirst());
  }

  @Override public Optional<Match> match(HttpServletRequest request, Object handler) {
    // Runtime invocations are resolved from the installed bean. HTTP matching remains owned by MVC.
    return Optional.empty();
  }
}
