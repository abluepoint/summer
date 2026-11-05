package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Current MENU qualification with an enabled ancestor closure; never grants interface actions. */
@Service
public class MenuNavigationService {
  private final JdbcTemplate db;
  private final ManagedPermissionAccess access;
  private final TransactionTemplate fresh;
  private final MenuPublicationState publication;

  public MenuNavigationService(
      JdbcTemplate db,
      ManagedPermissionAccess access,
      PlatformTransactionManager transactions,
      MenuPublicationState publication) {
    this.publication = publication;
    this.db = db;
    this.access = access;
    fresh = new TransactionTemplate(transactions);
    fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    fresh.setReadOnly(true);
  }

  public Map<String, Object> visible(Domain domain, Authentication authentication) {
    for (int attempt = 0; attempt < 2; attempt++) {
      Map<String, Object> result;
      try {
        result = fresh.execute(status -> snapshot(domain, authentication));
      } catch (org.springframework.dao.DataAccessException
          | org.springframework.transaction.TransactionException failure) {
        throw com.abluepoint.summer.common.error.SummerException.withMessage(
            com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
            "Current navigation authorization unavailable");
      }
      if (result != null) return result;
    }
    throw new AccessDeniedException("Authorization changed while reading navigation");
  }

  private Map<String, Object> snapshot(Domain domain, Authentication authentication) {
    if (!access.active(domain, authentication)
        || db.queryForObject(
                "select count(*) from summer_auth_user where username=? and active=true",
                Integer.class,
                authentication.getName())
            != 1) throw new AccessDeniedException("Active managed domain identity required");
    var modes =
        db.queryForList(
            "select engine_mode from summer_authorization_domain where domain_key=?",
            String.class,
            domain.key());
    if (modes.size() != 1 || !Set.of("LEGACY", "MANAGED").contains(modes.getFirst()))
      throw new AccessDeniedException("Known authorization domain mode required");
    if ("LEGACY".equals(modes.getFirst()))
      return Map.of("engineMode", "LEGACY", "manifestVersion", 0, "items", List.of());
    long revision = revision(domain);
    Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
    var state = publication.read();
    long manifestVersion = state.manifestVersion();
    for (var row :
        db.queryForList(
            "select * from summer_menu_manifest_item where manifest_version=? order by"
                + " sort_order,menu_id",
            manifestVersion)) {
      String id = row.get("menu_id").toString();
      var item = new LinkedHashMap<String, Object>();
      item.put("menuId", id);
      item.put("path", row.get("path"));
      item.put("parentMenuId", row.get("parent_menu_id"));
      item.put("titleKey", row.get("title_key"));
      item.put("iconKey", row.get("icon_key"));
      item.put("sortOrder", row.get("sort_order"));
      item.put("enabled", true);
      item.put("version", 1L);
      nodes.put(id, item);
      manifestVersion =
          Math.max(manifestVersion, ((Number) row.get("manifest_version")).longValue());
    }
    // Stable bootstrap identities survive before the first full frontend manifest publication.
    if (!state.published())
      for (var page : com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerMenus.PAGES) {
        String id = page.id();
        nodes.computeIfAbsent(
            id,
            key -> {
              var item = new LinkedHashMap<String, Object>();
              item.put("menuId", id);
              item.put("path", "/" + id);
              item.put("titleKey", page.title());
              item.put("iconKey", id);
              item.put("sortOrder", 0);
              item.put("enabled", true);
              item.put("version", 1L);
              return item;
            });
      }
    if (domain.kind().equals("ENTERPRISE"))
      for (var row :
          db.queryForList(
              "select * from summer_menu_manifest_override where enterprise_id=?", domain.id())) {
        var item = nodes.get(row.get("menu_id").toString());
        if (item == null) continue;
        if (row.get("enabled") != null) item.put("enabled", row.get("enabled"));
        if (row.get("parent_menu_id") != null) {
          String parent = row.get("parent_menu_id").toString();
          item.put("parentMenuId", parent.isBlank() ? null : parent);
        }
        if (row.get("sort_order") != null) item.put("sortOrder", row.get("sort_order"));
        item.put("version", row.get("version"));
      }
    for (var row :
        db.queryForList(
            "select * from summer_menu_domain_layout where domain_key=?", domain.key())) {
      var item = nodes.get(row.get("menu_id").toString());
      if (item == null) continue;
      if (row.get("enabled") != null) item.put("enabled", row.get("enabled"));
      if (row.get("parent_menu_id") != null) {
        String parent = row.get("parent_menu_id").toString();
        item.put("parentMenuId", parent.isBlank() ? null : parent);
      }
      if (row.get("sort_order") != null) item.put("sortOrder", row.get("sort_order"));
      item.put("version", row.get("version"));
    }
    Set<String> qualified = new HashSet<>(), visible = new HashSet<>();
    for (String id : nodes.keySet()) {
      if (!enabledBranch(id, nodes) || !access.allows(domain, authentication, "MENU", id)) continue;
      qualified.add(id);
      String cursor = id;
      while (cursor != null && !cursor.isBlank()) {
        visible.add(cursor);
        Object parent = nodes.get(cursor).get("parentMenuId");
        cursor = parent == null ? null : parent.toString();
      }
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (String id : visible) {
      var item = nodes.get(id);
      boolean page = qualified.contains(id);
      item.put("navOnly", !page);
      item.put("pageEligible", page);
      item.put("actions", List.of());
      items.add(item);
    }
    items.sort(
        Comparator.<Map<String, Object>>comparingInt(x -> ((Number) x.get("sortOrder")).intValue())
            .thenComparing(x -> x.get("menuId").toString()));
    if (revision != revision(domain)
        || !state.equals(publication.read())
        || !modes.equals(
            db.queryForList(
                "select engine_mode from summer_authorization_domain where domain_key=?",
                String.class,
                domain.key()))) return null;
    return Map.of(
        "engineMode",
        "MANAGED",
        "manifestVersion",
        manifestVersion,
        "authorizationRevision",
        revision,
        "items",
        items);
  }

  private boolean enabledBranch(String id, Map<String, Map<String, Object>> nodes) {
    Set<String> seen = new HashSet<>();
    String cursor = id;
    while (cursor != null && !cursor.isBlank()) {
      var item = nodes.get(cursor);
      if (item == null || !Boolean.TRUE.equals(item.get("enabled")) || !seen.add(cursor))
        return false;
      Object parent = item.get("parentMenuId");
      cursor = parent == null ? null : parent.toString();
    }
    return true;
  }

  private long revision(Domain domain) {
    return db.queryForObject(
        "select revision from summer_authorization_domain where domain_key=?",
        Long.class,
        domain.key());
  }
}
