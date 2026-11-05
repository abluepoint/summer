package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

/** Projects the already evaluated range onto code-registered business columns. */
public final class RegisteredScopeProjection {
  public record Columns(String domainKind, String domainColumn, String domainAttribute,
      String tenantColumn, String tenantAttribute, String ownerColumn, String ownerAttribute,
      String departmentColumn, String departmentAttribute) {}

  private final DataReadService.Scope scope;
  private final Columns columns;

  public RegisteredScopeProjection(DataReadService.Scope scope, Columns columns) {
    this.scope = Objects.requireNonNull(scope);
    this.columns = Objects.requireNonNull(columns);
    if (!columns.domainKind().equals(scope.domain().kind()))
      throw new AccessDeniedException("Data mapping belongs to another domain kind");
  }

  public Domain domain() { return scope.domain(); }

  /** The caller ANDs the resource/action's existing business predicates with this range. */
  public String predicate() {
    return (columns.domainColumn() == null ? "" : columns.domainColumn() + "=:scopeDomain and ")
        + columns.tenantColumn() + "=:scopeTenant and ("
        + (scope.all() ? "1=1" :
            (scope.self() ? columns.ownerColumn() + "=:scopeSubject" : "1=0")
            + (scope.departmentIds().isEmpty() ? "" : " or " + columns.departmentColumn() + " in (:scopeDepartments)"))
        + ")";
  }

  public Map<String, Object> bindings() {
    var bindings = new java.util.HashMap<String, Object>(scope.bindings());
    if (columns.domainColumn() == null) bindings.remove("scopeDomain");
    return Map.copyOf(bindings);
  }

  public <T> Specification<T> specification() {
    return (root, query, builder) -> builder.and(
        columns.domainAttribute() == null ? builder.conjunction()
            : builder.equal(root.get(columns.domainAttribute()), scope.domain().key()),
        builder.equal(root.get(columns.tenantAttribute()), scope.domain().id()),
        scope.all() ? builder.conjunction() : builder.or(
            scope.self() ? builder.equal(root.get(columns.ownerAttribute()), scope.subject()) : builder.disjunction(),
            scope.departmentIds().isEmpty() ? builder.disjunction()
                : root.get(columns.departmentAttribute()).in(scope.departmentIds())));
  }

  public boolean allows(String department, String owner) {
    return scope.all() || scope.self() && Objects.equals(scope.subject(), owner)
        || department != null && scope.departmentIds().contains(department);
  }

  public String fingerprint() {
    return scope.domain().key() + ":" + scope.resource().key() + ":" + scope.revision()
        + ":" + scope.all() + ":" + scope.self() + ":" + scope.subject() + ":" + scope.departmentIds();
  }
}
