package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.sample.dictionary.PlatformDictionaryOptions;
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
  private final MenuCatalogService catalog;

  public MenuNavigationService(
      JdbcTemplate db,
      ManagedPermissionAccess access,
      PlatformTransactionManager transactions,
      MenuPublicationState publication, MenuCatalogService catalog) {
    this.catalog = catalog;
    this.publication = publication;
    this.db = db;
    this.access = access;
    fresh = new TransactionTemplate(transactions);
    fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    fresh.setReadOnly(true);
  }

  public Map<String, Object> visible(Domain domain, Authentication authentication) {
    return visible(domain, authentication, null);
  }

  public Map<String, Object> visible(Domain domain, Authentication authentication, String clientCode) {
    for (int attempt = 0; attempt < 2; attempt++) {
      Map<String, Object> result;
      try {
        result = fresh.execute(status -> snapshot(domain, authentication, clientCode));
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

  private Map<String, Object> snapshot(Domain domain, Authentication authentication, String clientCode) {
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
    for (var item : catalog.effectiveCatalog()) nodes.put((String) item.get("menuId"), item);
    Set<String> qualified = new HashSet<>(), visible = new HashSet<>();
    // Older catalogs use "roles" for both domains. An explicit platform page owns its
    // destination even when disabled, assigned to another client, or lacking a grant.
    boolean separatePlatformRoles = nodes.containsKey("platform/roles");
    for (String id : nodes.keySet()) {
      if (Domain.platform().equals(domain) ? separatePlatformRoles && "roles".equals(id)
          : Set.of("platform/roles","platform/administrators","platform/organization","platform/file-repositories","platform/files","platform/user-groups").contains(id)) continue;
      if (!matchesClient(nodes.get(id), clientCode) || !enabledBranch(id, nodes)
          || !access.allows(domain, authentication, "MENU", id)) continue;
      qualified.add(id);
      String cursor = id;
      while (cursor != null && !cursor.isBlank()) {
        if (matchesClient(nodes.get(cursor), clientCode)) visible.add(cursor);
        Object parent = nodes.get(cursor).get("parentMenuId");
        cursor = parent == null ? null : parent.toString();
      }
    }
    List<Map<String, Object>> items = new ArrayList<>();
    for (String id : visible) {
      var item = nodes.get(id);
      String parent = (String) item.get("parentMenuId");
      while (parent != null && !parent.isBlank() && !visible.contains(parent))
        parent = (String) nodes.get(parent).get("parentMenuId");
      item = new LinkedHashMap<>(item);
      item.put("parentMenuId", parent == null || parent.isBlank() ? null : parent);
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

  private static boolean matchesClient(Map<String, Object> item, String clientCode) {
    return clientCode == null || clientCode.equals(Objects.requireNonNullElse(item.get("clientCode"), PlatformDictionaryOptions.WEB_CLIENT));
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
