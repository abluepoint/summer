package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.sample.dictionary.PlatformDictionaryOptions;
import com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerMenus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Menu metadata and domain layout, independent of MENU and INTERFACE associations. */
@Service
public class MenuCatalogService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;
  private final MenuPublicationState publication;

  private final PlatformDictionaryOptions dictionaries;

  public MenuCatalogService(
      JdbcTemplate db,
      ObjectMapper json,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      MenuPublicationState publication, PlatformDictionaryOptions dictionaries) {
    this.dictionaries = dictionaries;
    this.publication = publication;
    this.db = db;
    this.json = json;
    this.audit = audit;
    this.events = events;
  }

  private static SummerException invalid(String message) {
    return SummerException.withMessage(PermissionErrorCode.INVALID, message);
  }

  private long version() {
    return publication.read().manifestVersion();
  }

  private List<Map<String, Object>> catalog() {
    var items = new ArrayList<Map<String, Object>>();
    for (var row :
        db.queryForList(
            "select * from summer_menu_manifest_item where manifest_version=(select"
                + " manifest_version from summer_menu_publication where publication_id='global')"
                + " order by sort_order,menu_id")) {
      var item = new LinkedHashMap<String, Object>();
      item.put("clientCode", row.get("client_code"));
      item.put("menuId", row.get("menu_id"));
      item.put("parentMenuId", row.get("parent_menu_id"));
      item.put("path", row.get("path"));
      item.put("titleKey", row.get("title_key"));
      item.put("iconKey", row.get("icon_key"));
      item.put("sortOrder", row.get("sort_order"));
      item.put("enabled", true);
      item.put("version", 1L);
      item.put("actions", List.of());
      items.add(item);
    }
    if (!publication.read().published()) {
      var pages = new ArrayList<>(ManagementConsumerMenus.PAGES);
      if (db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id=?",
          Integer.class, Domain.platform().key(), "platform-directory-roster") == 1)
        pages.addAll(ManagementConsumerMenus.PLATFORM_DIRECTORY_PAGES);
      if (db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id=?",
          Integer.class, Domain.platform().key(), "file-repositories-menu") == 1) {
        pages.add(ManagementConsumerMenus.FILE_REPOSITORIES);
      }
      if (db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id=?",
          Integer.class, Domain.platform().key(), "files-menu") == 1) {
        pages.add(ManagementConsumerMenus.FILE_MANAGER);
      }
      if (pages.contains(ManagementConsumerMenus.FILE_REPOSITORIES) || pages.contains(ManagementConsumerMenus.FILE_MANAGER))
        pages.add(ManagementConsumerMenus.FILE_DIRECTORY);
      if (db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id=?",
          Integer.class, Domain.platform().key(), "user-groups-menu") == 1) {
        if (pages.stream().noneMatch(page -> page.id().equals("platform-management")))
          pages.add(new ManagementConsumerMenus.Page("platform-management", "平台管理"));
        pages.add(new ManagementConsumerMenus.Page("platform/user-groups", "平台用户组管理"));
      }
      for (var page : pages) {
        String id = page.id();
        var item = new LinkedHashMap<String, Object>();
        item.put("clientCode", PlatformDictionaryOptions.WEB_CLIENT);
        item.put("menuId", id);
        item.put("parentMenuId", ManagementConsumerMenus.platformParent(id));
        item.put("path", "/" + id);
        item.put("titleKey", page.title());
        item.put("iconKey", id);
        item.put("sortOrder", 0);
        item.put("enabled", true);
        item.put("version", 1L);
        item.put("actions", List.of());
        items.add(item);
      }
    }
    return items;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> read(Domain d, boolean platformManifest) {
    var items = effectiveCatalog();
    items.sort(Comparator.<Map<String, Object>>comparingInt(item -> ((Number) item.get("sortOrder")).intValue())
        .thenComparing(item -> item.get("menuId").toString()));
    if (platformManifest && d.equals(Domain.platform()))
      return Map.of("manifestVersion", version(), "items", items,
          "clientOptions", dictionaries.published(PlatformDictionaryOptions.MENU_CLIENT));
    return Map.of("manifestVersion", version(), "items", items);
  }

  List<Map<String, Object>> effectiveCatalog() {
    var items = catalog();
    overlay(items, db.queryForList("select * from summer_menu_domain_layout where domain_key=?", Domain.platform().key()));
    return items;
  }

  // A layout PATCH has its own revision. Check the complete editor snapshot, including deleted rows.
  private void checkLayoutVersions(Object expected, Map<String, Map<String,Object>> existing) {
    if (!(expected instanceof Map<?,?> versions)) throw invalid("Expected menu layout versions");
    var current = new TreeMap<String,Object>();
    existing.forEach((id, item) -> current.put(id, item.get("version")));
    if (!json.writeValueAsString(current).equals(json.writeValueAsString(canonicalMetadata(versions))))
      throw SummerException.withMessage(SampleTransportErrorCode.PERMISSION_STALE, "Menu layout has changed");
  }

  private void publishLayout(Domain domain, Map<String,Object> item, Map<String,Object> previous) {
    String id = (String) item.get("menuId");
    // Parent and sort order have just been written to the manifest. Release old overrides
    // when this editor changes the layout so navigation uses those values immediately.
    if (previous != null && Objects.equals(previous.get("enabled"), item.get("enabled"))
        && Objects.equals(previous.get("parentMenuId"), item.get("parentMenuId"))
        && Objects.equals(previous.get("sortOrder"), item.get("sortOrder"))) return;
    db.update("insert into summer_menu_domain_layout(layout_id,domain_key,menu_id,enabled,version) values (?,?,?,?,2)"
        + " on duplicate key update enabled=values(enabled),parent_menu_id=null,sort_order=null,version=version+1",
        domain.key() + ":" + id, domain.key(), id, item.get("enabled"));
  }

  private static void overlay(List<Map<String, Object>> items, List<Map<String, Object>> rows) {
    var byId = new HashMap<Object, Map<String,Object>>();
    for (var item : items) byId.put(item.get("menuId"), item);
    for (var row : rows) {
      var item = byId.get(row.get("menu_id"));
      if (item == null) continue;
      if (row.get("enabled") != null) item.put("enabled", row.get("enabled"));
      if (row.get("parent_menu_id") != null) item.put("parentMenuId", parent(row.get("parent_menu_id")));
      if (row.get("sort_order") != null) item.put("sortOrder", row.get("sort_order"));
      item.put("version", row.get("version"));
    }
  }

  private static void condition(String header, long version) {
    if (header == null)
      throw SummerException.withMessage(
          SampleTransportErrorCode.PERMISSION_CONDITION_REQUIRED, "If-Match is required");
    if (!header.matches("\"[0-9]+\"")) throw invalid("Invalid If-Match");
    if (!header.equals("\"" + version + "\""))
      throw SummerException.withMessage(
          SampleTransportErrorCode.PERMISSION_STALE, "Menu metadata has changed");
  }

  private static String text(Object value, int max, boolean empty) {
    if (!(value instanceof String s) || s.length() > max || !empty && s.isBlank())
      throw invalid("Invalid menu text");
    return s;
  }

  private static String parent(Object value) {
    if (value == null) return null;
    String name = text(value, 160, true);
    return name.isBlank() ? null : name;
  }

  private Object canonicalMetadata(Object value) {
    if (value instanceof Map<?, ?> fields) {
      var sorted = new TreeMap<String, Object>();
      fields.forEach((key, item) -> sorted.put(key.toString(), canonicalMetadata(item)));
      return sorted;
    }
    if (value instanceof List<?> items)
      return items.stream().map(this::canonicalMetadata)
          .sorted(Comparator.comparing(json::writeValueAsString)).toList();
    return value;
  }

  private static int order(Object value) {
    if (!(value instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 0)
      throw invalid("Nonnegative integer sortOrder required");
    return n.intValue();
  }

  private static void hierarchy(List<Map<String, Object>> items) {
    var parents = new HashMap<String, String>();
    var paths = new HashSet<String>();
    for (var item : items) {
      String id = text(item.get("menuId"), 160, false);
      if (parents.containsKey(id) || !paths.add(text(item.get("path"), 500, false)))
        throw invalid("Duplicate menu identity or path");
      Object parent = item.get("parentMenuId");
      parents.put(id, parent == null ? null : text(parent, 160, true));
    }
    for (String id : parents.keySet()) {
      var seen = new HashSet<String>();
      String cursor = id;
      while (cursor != null && !cursor.isBlank()) {
        if (!parents.containsKey(cursor) || !seen.add(cursor))
          throw invalid("Missing or cyclic menu parent");
        cursor = parents.get(cursor);
      }
    }
  }

  @Transactional
  public PermissionService.Result mutate(
      Domain d,
      String actor,
      String menuId,
      Map<String, Object> body,
      String etag,
      String key,
      String requestId) {
    if (!d.equals(Domain.platform()))
      throw new org.springframework.security.access.AccessDeniedException("Menus are managed by the platform");
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw invalid("Idempotency-Key required");
    var mode =
        db.queryForList(
            "select engine_mode from summer_authorization_domain where domain_key=? for update",
            String.class,
            d.key());
    if (mode.size() != 1 || !mode.getFirst().equals("MANAGED"))
      throw SummerException.withMessage(PermissionErrorCode.CONFLICT, "Managed domain required");
    publication.lock(d);
    boolean publish = menuId == null;
    if (publish && !d.kind().equals("PLATFORM")) throw invalid("Platform manifest required");
    String operation = publish ? "menu:manifest" : "menu:layout:" + menuId;
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          json.writeValueAsString(
                                  Arrays.asList(operation, canonicalMetadata(body), etag))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
    var prior =
        db.queryForList(
            "select * from summer_permission_mutation where domain_key=? and actor=? and"
                + " operation_key=? and idempotency_key=?",
            d.key(),
            actor,
            operation,
            key);
    if (!prior.isEmpty()) {
      var row = prior.getFirst();
      if (!fingerprint.equals(row.get("fingerprint")))
        throw SummerException.withMessage(PermissionErrorCode.CONFLICT, "Idempotency key reused");
      return new PermissionService.Result(
          json.readValue(row.get("response_json").toString(), Map.class),
          ((Number) row.get("response_status")).intValue(),
          ((Number) row.get("response_version")).longValue());
    }
    long next;
    Map<String, Object> result;
    if (publish) {
      boolean editor = body.containsKey("layoutVersions");
      if (!body.keySet().equals(editor ? Set.of("manifestVersion", "items", "layoutVersions") : Set.of("manifestVersion", "items"))
          || !(body.get("items") instanceof List<?> raw)
          || raw.size() > 1000) throw invalid("Explicit menu items required");
      long current = version();
      condition(etag, current);
      if (!(body.get("manifestVersion") instanceof Number requested)
          || requested.longValue() != requested.doubleValue()
          || requested.longValue() < 1) throw invalid("A positive integer manifestVersion is required");
      if (requested.longValue() <= current)
        throw SummerException.withMessage(PermissionErrorCode.CONFLICT, "Manifest version must increase");
      next = requested.longValue();
      var items = new ArrayList<Map<String, Object>>();
      var clients = dictionaries.published(PlatformDictionaryOptions.MENU_CLIENT);
      var existing = new HashMap<String, Map<String,Object>>();
      for (var item : effectiveCatalog()) existing.put((String) item.get("menuId"), item);
      if (editor) checkLayoutVersions(body.get("layoutVersions"), existing);
      var allowedFields = new HashSet<>(Set.of("menuId", "parentMenuId", "path", "titleKey", "iconKey", "sortOrder", "clientCode"));
      if (editor) allowedFields.add("enabled");
      for (Object value : raw) {
        if (!(value instanceof Map<?, ?> fields)
            || !allowedFields.containsAll(fields.keySet())) throw invalid("Only menu metadata accepted");
        var item = new LinkedHashMap<String, Object>();
        String id = text(fields.get("menuId"), 160, false);
        if (!id.matches("[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._:-]+)*")) throw invalid("Invalid menuId");
        item.put("menuId", id);
        if (editor) {
          if (!(fields.get("enabled") instanceof Boolean)) throw invalid("Boolean enabled required");
          item.put("enabled", fields.get("enabled"));
        }
        String previous = (String) existing.getOrDefault(id, Map.of()).get("clientCode");
        String client = fields.containsKey("clientCode") ? text(fields.get("clientCode"), 80, false)
            : Objects.requireNonNullElse(previous, PlatformDictionaryOptions.WEB_CLIENT);
        if (!client.equals(previous) && clients.stream().noneMatch(option -> option.enabled() && client.equals(option.value())))
          throw invalid("请选择已发布且启用的所属客户端");
        item.put("clientCode", client);
        item.put("parentMenuId", parent(fields.get("parentMenuId")));
        String path = text(fields.get("path"), 500, false);
        if (!path.equals("/" + item.get("menuId"))
            || path.contains("?")
            || path.contains("#")
            || path.contains("//")
            || Arrays.asList(path.split("/")).contains(".."))
          throw invalid("Canonical local menu path required");
        item.put("path", path);
        item.put("titleKey", text(fields.get("titleKey"), 240, false));
        item.put("iconKey", text(fields.get("iconKey"), 120, true));
        item.put("sortOrder", order(fields.get("sortOrder")));
        items.add(item);
      }
      hierarchy(items);
      // Keep retired identities for existing links; only the highest published version is current.
      for (var item :
          items.stream().sorted(Comparator.comparing(x -> x.get("menuId").toString())).toList())
        db.update(
            "insert into"
                + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,client_code,capability_code,manifest_version,created_at,updated_at)"
                + " values (?,?,?,?,?,?,?,null,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) on"
                + " duplicate key update"
                + " parent_menu_id=values(parent_menu_id),path=values(path),title_key=values(title_key),icon_key=values(icon_key),sort_order=values(sort_order),client_code=values(client_code),capability_code=null,manifest_version=values(manifest_version),updated_at=CURRENT_TIMESTAMP(6)",
            item.get("menuId"),
            item.get("parentMenuId"),
            item.get("path"),
            item.get("titleKey"),
            item.get("iconKey"),
            item.get("sortOrder"),
            item.get("clientCode"),
            next);
      if (editor) for (var item : items) publishLayout(d, item, existing.get(item.get("menuId")));
      publication.published(next);
      result = read(d, true);
    } else {
      if (body.isEmpty()
          || !Set.of("enabled", "parentMenuId", "sortOrder").containsAll(body.keySet()))
        throw invalid("Only explicit layout fields accepted");
      var items = (List<Map<String, Object>>) read(d, false).get("items");
      var item =
          items.stream()
              .filter(x -> x.get("menuId").equals(menuId))
              .findFirst()
              .orElseThrow(() -> invalid("Unknown current menu"));
      // Shared catalog row lock also serializes global retirement with a layout write.
      db.queryForList(
          "select menu_id from summer_menu_manifest_item where menu_id=? for update", menuId);
      condition(etag, ((Number) item.get("version")).longValue());
      if (body.containsKey("enabled") && !(body.get("enabled") instanceof Boolean))
        throw invalid("Boolean enabled required");
      if (body.containsKey("parentMenuId")) parent(body.get("parentMenuId"));
      if (body.containsKey("sortOrder")) order(body.get("sortOrder"));
      var previousRows = db.queryForList(
          "select enabled,parent_menu_id,sort_order from summer_menu_domain_layout where domain_key=? and menu_id=?",
          d.key(), menuId);
      Map<String, Object> previous = previousRows.isEmpty() ? Map.of() : previousRows.getFirst();
      item.putAll(body);
      if (body.containsKey("parentMenuId")) item.put("parentMenuId", parent(body.get("parentMenuId")));
      hierarchy(items);
      next = ((Number) item.get("version")).longValue() + 1;
      item.put("version", next);
      db.update(
          "insert into"
              + " summer_menu_domain_layout(layout_id,domain_key,menu_id,enabled,parent_menu_id,sort_order,version)"
              + " values (?,?,?,?,?,?,?) on duplicate key update"
              + " enabled=values(enabled),parent_menu_id=values(parent_menu_id),sort_order=values(sort_order),version=values(version)",
          d.key() + ":" + menuId,
          d.key(),
          menuId,
          body.containsKey("enabled") ? body.get("enabled") : previous.get("enabled"),
          body.containsKey("parentMenuId") ? Objects.toString(parent(body.get("parentMenuId")), "") : previous.get("parent_menu_id"),
          body.containsKey("sortOrder") ? body.get("sortOrder") : previous.get("sort_order"),
          next);
      result = item;
    }
    audit.mutation(
        d.key(),
        publish ? "manifest" : menuId,
        publish ? "MENU_MANIFEST_PUBLISHED" : "MENU_LAYOUT_CHANGED",
        requestId,
        actor);
    events.enqueue(d, "MENU", publish ? "manifest" : menuId, "MENU_CHANGED", next, requestId);
    db.update(
        "insert into summer_permission_mutation values (?,?,?,?,?,?,?,?)",
        d.key(),
        actor,
        operation,
        key,
        fingerprint,
        json.writeValueAsString(result),
        200,
        next);
    return new PermissionService.Result(result, 200, next);
  }
}
