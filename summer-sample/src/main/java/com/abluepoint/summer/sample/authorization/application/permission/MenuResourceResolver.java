package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MenuResourceResolver implements TrustedPermissionResourceResolver {
  private final JdbcTemplate db;
  private final MenuPublicationState publication;

  public MenuResourceResolver(JdbcTemplate db, MenuPublicationState publication) {
    this.db = db;
    this.publication = publication;
  }

  public String type() {
    return "MENU";
  }

  public Resource resolve(Domain domain, String id) {
    // The caller holds the domain lock before taking the shared publication/catalog locks.
    publication.lock(domain);
    var rows =
        db.queryForList(
            "select menu_id from summer_menu_manifest_item where menu_id=? and"
                + " manifest_version=(select manifest_version from summer_menu_publication where"
                + " publication_id='global') for update",
            id);
    if (rows.isEmpty()
        && !(Boolean.FALSE.equals(
                db.queryForObject(
                    "select published from summer_menu_publication where publication_id='global'",
                    Boolean.class))
            && List.of("roles", "permissions").contains(id)))
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "Unknown menu resource");
    return new Resource(
        id, "MENU", List.of("roles", "permissions").contains(id) ? "MANAGEMENT" : "MANIFEST");
  }
}
