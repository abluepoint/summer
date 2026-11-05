package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** An immutable range is consumed within the same fresh read as its authority revision. */
@Service
public class DataReadService {
  private final JdbcTemplate db;
  private final ManagedPermissionAccess access;
  private final DataResourceRegistry registry;
  private final TransactionTemplate fresh;

  public DataReadService(
      JdbcTemplate db,
      ManagedPermissionAccess access,
      DataResourceRegistry registry,
      PlatformTransactionManager tm) {
    this.db = db;
    this.access = access;
    this.registry = registry;
    fresh = new TransactionTemplate(tm);
    fresh.setReadOnly(true);
    fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public record Scope(
      Domain domain,
      DataResourceRegistry.Resource resource,
      long revision,
      boolean all,
      boolean self,
      String subject,
      Set<String> departmentIds) {
    public Scope {
      departmentIds = Collections.unmodifiableSet(new TreeSet<>(departmentIds));
    }

    public Scope(
        Domain d,
        DataResourceRegistry.Resource r,
        long revision,
        boolean all,
        boolean self,
        String subject) {
      this(d, r, revision, all, self, subject, Set.of());
    }

    public String predicate() {
      return "domain_key=:scopeDomain and "
          + resource.tenantColumn()
          + "=:scopeTenant and "
          + resource.upperBoundColumn()
          + "=true and "
          + (all
              ? "1=1"
              : "("
                  + (self ? resource.selfColumn() + "=:scopeSubject" : "1=0")
                  + (departmentIds.isEmpty()
                      ? ""
                      : " or " + resource.departmentColumn() + " in (:scopeDepartments)")
                  + ")");
    }

    public Map<String, Object> bindings() {
      var b = new HashMap<String, Object>();
      b.put("scopeTenant", domain.id());
      b.put("scopeDomain", domain.key());
      if (self && !all) b.put("scopeSubject", subject);
      if (!all && !departmentIds.isEmpty()) b.put("scopeDepartments", List.copyOf(departmentIds));
      return b;
    }

    public <T> Specification<T> specification() {
      return (root, q, cb) ->
          cb.and(
              cb.equal(root.get("domainKey"), domain.key()),
              cb.equal(root.get(resource.tenantAttribute()), domain.id()),
              cb.isTrue(root.get(resource.upperBoundAttribute())),
              all
                  ? cb.conjunction()
                  : cb.or(
                      self
                          ? cb.equal(root.get(resource.selfAttribute()), subject)
                          : cb.disjunction(),
                      departmentIds.isEmpty()
                          ? cb.disjunction()
                          : root.get(resource.departmentAttribute()).in(departmentIds)));
    }
  }

  public <T> T read(
      Domain d, String resource, String action, Authentication a, Function<Scope, T> query) {
    return readCurrent(d, resource, action, a, null, query);
  }

  /** A diagnostic can narrow the existing grant set, never supply its own range. */
  public <T> T readPermission(Domain d, String resource, String action, Authentication a,
      String permissionId, Function<Scope, T> query) {
    if (permissionId == null || permissionId.isBlank())
      throw new AccessDeniedException("A current permission identity is required");
    return readCurrent(d, resource, action, a, permissionId, query);
  }

  private <T> T readCurrent(Domain d, String resource, String action, Authentication a,
      String permissionId, Function<Scope, T> query) {
    var registration = registry.require(resource, action);
    try {
      for (int attempt = 0; attempt < 2; attempt++) {
        T result =
            fresh.execute(
                tx -> {
                  var before = state(d);
                  if (!"MANAGED".equals(before.get("engine_mode")))
                    throw new AccessDeniedException("Managed data domain required");
                  if (d.kind().equals("ENTERPRISE")
                      && db.queryForObject(
                              "select count(*) from summer_enterprise"
                                  + " where enterprise_id=? and"
                                  + " status='ACTIVE'",
                              Integer.class,
                              d.id())
                          == 0) throw new AccessDeniedException("Active enterprise required");
                  if (access.authenticated(a) && !access.active(d, a))
                    throw new AccessDeniedException("Active identity required");
                  boolean all = false, self = false;
                  Set<String> departments = new TreeSet<>();
                  for (var p :
                      db.queryForList(
                          "select p.*,s.scope_preset from"
                              + " summer_permission p join"
                              + " summer_permission_data_scope s on"
                              + " s.domain_key=p.domain_key and"
                              + " s.permission_id=p.permission_id where"
                              + " p.domain_key=? and p.type='DATA' and"
                              + " s.data_resource_key=? and s.action=?"
                              + (permissionId == null ? "" : " and p.permission_id=?"),
                          permissionId == null ? new Object[]{d.key(), resource, action}
                              : new Object[]{d.key(), resource, action, permissionId})) {
                    if (!access.applies(d, a, p)) continue;
                    if ("TENANT_ALL".equals(p.get("scope_preset"))) all = true;
                    else if ("SELF".equals(p.get("scope_preset")) && access.active(d, a))
                      self = true;
                    else if (d.kind().equals("ENTERPRISE"))
                      departments.addAll(departments(d, a, p));
                  }
                  T value =
                      query.apply(
                          new Scope(
                              d,
                              registration,
                              ((Number) before.get("revision")).longValue(),
                              all,
                              self,
                              a == null ? null : a.getName(),
                              departments));
                  return before.equals(state(d)) ? value : null;
                });
        if (result != null) return result;
      }
      throw new AccessDeniedException("Data authorization changed during read");
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException e) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "Current data authorization unavailable");
    }
  }

  private Set<String> departments(Domain d, Authentication a, Map<String, Object> permission) {
    String preset = String.valueOf(permission.get("scope_preset"));
    if (!Set.of("DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "SPECIFIED_DEPARTMENTS")
        .contains(preset)) return Set.of();
    var links =
        db.queryForList(
            "select unit_id,parent_id from summer_organization_unit where enterprise_id=? and"
                + " status='ACTIVE'",
            d.id());
    Set<String> active = new HashSet<>();
    for (var link : links) active.add((String) link.get("unit_id"));
    Set<String> result = new TreeSet<>();
    if (preset.equals("SPECIFIED_DEPARTMENTS")) {
      result.addAll(
          db.queryForList(
              "select department_id from summer_permission_selected_department where domain_key=?"
                  + " and permission_id=?",
              String.class,
              d.key(),
              permission.get("permission_id")));
      result.retainAll(active);
      return result;
    }
    if (!access.active(d, a)) return Set.of();
    result.addAll(
        db.queryForList(
            "select m.organization_id from summer_organization_membership m join"
                + " summer_enterprise_member e on e.enterprise_id=m.enterprise_id and"
                + " e.member_id=m.member_id where m.enterprise_id=? and e.subject=? and"
                + " e.status='ACTIVE'",
            String.class,
            d.id(),
            a.getName()));
    result.retainAll(active);
    if (preset.equals("DEPARTMENT_AND_DESCENDANTS")) {
      boolean changed;
      do {
        changed = false;
        for (var link : links)
          if (link.get("parent_id") != null && result.contains(link.get("parent_id")))
            changed |= result.add((String) link.get("unit_id"));
      } while (changed);
    }
    return result;
  }

  private Map<String, Object> state(Domain d) {
    var rows =
        db.queryForList(
            "select engine_mode,revision from summer_authorization_domain where" + " domain_key=?",
            d.key());
    if (rows.size() != 1 || !(rows.getFirst().get("revision") instanceof Number revision)
        || revision.longValue() < 0)
      throw new AccessDeniedException("Unconfirmed authorization domain state");
    return rows.getFirst();
  }
}
