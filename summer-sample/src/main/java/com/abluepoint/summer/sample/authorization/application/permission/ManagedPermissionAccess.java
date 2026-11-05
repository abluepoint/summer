package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/** Persistent role facts shared by request authorization and permission management. */
@Service
public class ManagedPermissionAccess {
  public static final String RESOURCE_ATTRIBUTE = "summer.managed.resource";
  private final JdbcTemplate db;
  private final com.abluepoint.summer.sample.authorization.AuthorizationDomainVersions versions;

  public ManagedPermissionAccess(JdbcTemplate db,
      com.abluepoint.summer.sample.authorization.AuthorizationDomainVersions versions) {
    this.db = db;
    this.versions = versions;
  }

  public boolean managed(Domain d) {
    return versions.managed(d);
  }

  public boolean authenticated(Authentication a) {
    return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
  }

  public boolean active(Domain d, Authentication a) {
    if (!authenticated(a)) return false;
    var users =
        db.queryForList(
            "select active from summer_auth_user where username=?", Boolean.class, a.getName());
    if (users.size() != 1 || !Boolean.TRUE.equals(users.getFirst())) return false;
    return !d.kind().equals("ENTERPRISE")
        || db.queryForObject(
                "select count(*) from summer_enterprise_member m join summer_enterprise e on"
                    + " e.enterprise_id=m.enterprise_id where m.enterprise_id=? and m.subject=? and"
                    + " m.status='ACTIVE' and e.status='ACTIVE'",
                Integer.class,
                d.id(),
                a.getName())
            > 0;
  }

  public List<Map<String, Object>> permissions(Domain d, Authentication a) {
    if (!active(d, a)) return List.of();
    return db.queryForList(
        "select p.*,r.role_id,r.name as role_name from summer_permission_user_role u join"
            + " summer_permission_role dr on dr.domain_key=u.domain_key and dr.role_id=u.role_id"
            + " join summer_authorization_role r on r.role_id=dr.role_id join"
            + " summer_role_permission rp on rp.domain_key=dr.domain_key and rp.role_id=dr.role_id"
            + " join summer_permission p on p.domain_key=rp.domain_key and"
            + " p.permission_id=rp.permission_id where u.domain_key=? and u.username=? and"
            + " r.status='ACTIVE' and p.status='ACTIVE' order by p.permission_id,r.role_id",
        d.key(),
        a.getName());
  }

  public boolean applies(Domain d, Authentication a, Map<String, Object> p) {
    if (!"ACTIVE".equals(p.get("status"))) return false;
    if (authenticated(a) && !active(d, a)) return false;
    return switch (p.get("access_mode").toString()) {
      case "PUBLIC" -> true;
      case "AUTHENTICATED" -> active(d, a);
      case "AUTHORIZED" ->
          permissions(d, a).stream()
              .anyMatch(x -> x.get("permission_id").equals(p.get("permission_id")));
      default -> false;
    };
  }

  public boolean allows(Domain d, Authentication a, String type, String resource) {
    return db
        .queryForList(
            "select p.* from summer_permission_resource x join summer_permission p on"
                + " p.domain_key=x.domain_key and p.permission_id=x.permission_id where"
                + " x.domain_key=? and x.resource_type=? and x.resource_id=? and p.type=?",
            d.key(),
            type,
            resource,
            type)
        .stream()
        .anyMatch(p -> applies(d, a, p));
  }

  public void requireManagement(Domain d, Authentication a, boolean write) {
    // An explicit persisted management resource; no old capability/claim business grant.
    var context =
        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
    String resource =
        context instanceof org.springframework.web.context.request.ServletRequestAttributes attrs
            ? (String) attrs.getRequest().getAttribute(RESOURCE_ATTRIBUTE)
            : null;
    if (!active(d, a)
        || resource == null
        || !roleAllows(d, a, resource)) throw new AccessDeniedException("Current role management permission required");
    if (!delegated(d, a, write))
      throw new AccessDeniedException("Delegated management scope required");
  }

  /** Current role grant for a management resource, independent of PUBLIC access mode. */
  public boolean roleAllows(Domain d, Authentication a, String resource) {
    if (!active(d, a)) return false;
    return db.queryForObject(
                "select count(*) from summer_permission_user_role u join summer_authorization_role"
                    + " r on r.role_id=u.role_id join summer_role_permission rp on"
                    + " rp.domain_key=u.domain_key and rp.role_id=u.role_id join summer_permission"
                    + " p on p.domain_key=rp.domain_key and p.permission_id=rp.permission_id join"
                    + " summer_permission_resource x on x.domain_key=p.domain_key and"
                    + " x.permission_id=p.permission_id where u.domain_key=? and u.username=? and"
                    + " r.status='ACTIVE' and p.status='ACTIVE' and x.resource_type='INTERFACE' and"
                    + " x.resource_id=?",
                Integer.class,
                d.key(),
                a.getName(),
                resource)
             > 0;
  }

  /** OAuth scopes cap client delegation; they never create a business grant. */
  public boolean delegated(Domain d, Authentication a, boolean write) {
    if (!(a instanceof JwtAuthenticationToken jwt)
        || "local-console-session".equals(jwt.getToken().getTokenValue())) return true;
    String scope =
        (d.kind().equals("PLATFORM") ? "platform.capabilities." : "enterprise.roles.")
            + (write ? "write" : "read");
    Object raw = jwt.getToken().getClaims().get("scope");
    return raw instanceof Collection<?> values
        ? values.contains(scope)
        : raw instanceof String text && Arrays.asList(text.split(" ")).contains(scope);
  }
}
