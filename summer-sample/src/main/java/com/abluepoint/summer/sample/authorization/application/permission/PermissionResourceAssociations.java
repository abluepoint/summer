package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared interface/menu association facts. Callers own revision, audit and receipt in this
 * transaction.
 */
@Service
public class PermissionResourceAssociations {
  private final JdbcTemplate db;

  public PermissionResourceAssociations(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void bind(Domain domain, String permissionId, Resource resource) {
    if (db.queryForList(
            "select revision from summer_authorization_domain where domain_key=? for update",
            domain.key())
        .isEmpty())
      throw SummerException.withMessage(
          PermissionErrorCode.CONFLICT, "Authorization domain is not initialized");
    var rows =
        db.queryForList(
            "select type,access_mode from summer_permission where domain_key=? and permission_id=?"
                + " for update",
            domain.key(),
            permissionId);
    if (rows.isEmpty())
      throw SummerException.withMessage(PermissionErrorCode.NOT_FOUND, "Permission not found");
    if (!resource.type().equals(rows.getFirst().get("type")))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID, "Resource type differs from permission");
    int conflicts =
        db.queryForObject(
            "select count(*) from summer_permission_resource x join summer_permission p on"
                + " p.domain_key=x.domain_key and p.permission_id=x.permission_id where"
                + " x.domain_key=? and x.resource_type=? and x.resource_id=? and p.access_mode<>?",
            Integer.class,
            domain.key(),
            resource.type(),
            resource.id(),
            rows.getFirst().get("access_mode"));
    if (conflicts > 0)
      throw SummerException.withMessage(
          PermissionErrorCode.CONFLICT,
          "Resource permissions must share one access mode, including disabled permissions");
    db.update(
        "insert into summer_permission_resource values (?,?,?,?) on duplicate key update"
            + " permission_id=permission_id",
        domain.key(),
        permissionId,
        resource.type(),
        resource.id());
  }
}
