package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
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

  public MenuCatalogService(
      JdbcTemplate db,
      ObjectMapper json,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      MenuPublicationState publication) {
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
    if (!publication.read().published())
      for (String id : List.of("roles", "permissions")) {
        var item = new LinkedHashMap<String, Object>();
        item.put("menuId", id);
        item.put("parentMenuId", null);
        item.put("path", "/" + id);
        item.put("titleKey", id.equals("roles") ? "角色与权限" : "权限管理");
        item.put("iconKey", id);
        item.put("sortOrder", 0);
        item.put("enabled", true);
        item.put("version", 1L);
        item.put("actions", List.of());
        items.add(item);
      }
    return items;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> read(Domain d, boolean platformManifest) {
    var items = catalog();
    if (!platformManifest) {
      // Existing enterprise layout remains compatible; new writes use explicit domain keys.
      if (d.kind().equals("ENTERPRISE"))
        overlay(
            items,
            db.queryForList(
                "select * from summer_menu_manifest_override where enterprise_id=?", d.id()));
      overlay(
          items,
          db.queryForList("select * from summer_menu_domain_layout where domain_key=?", d.key()));
    }
    return Map.of("manifestVersion", version(), "items", items);
  }

  private static void overlay(List<Map<String, Object>> items, List<Map<String, Object>> rows) {
    for (var row : rows)
      for (var item : items)
        if (item.get("menuId").equals(row.get("menu_id"))) {
          for (var field :
              Map.of(
                      "enabled",
                      "enabled",
                      "parentMenuId",
                      "parent_menu_id",
                      "sortOrder",
                      "sort_order",
                      "version",
                      "version")
                  .entrySet())
            if (row.get(field.getValue()) != null)
              item.put(field.getKey(), row.get(field.getValue()));
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
                                  Arrays.asList(operation, new TreeMap<>(body), etag))
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
      if (!body.keySet().equals(Set.of("manifestVersion", "items"))
          || !(body.get("items") instanceof List<?> raw)
          || raw.size() > 1000) throw invalid("Explicit menu items required");
      long current = version();
      condition(etag, current);
      if (!(body.get("manifestVersion") instanceof Number requested)
          || requested.longValue() != requested.doubleValue()
          || requested.longValue() <= current) throw invalid("Manifest version must increase");
      next = requested.longValue();
      var items = new ArrayList<Map<String, Object>>();
      for (Object value : raw) {
        if (!(value instanceof Map<?, ?> fields)
            || !Set.of("menuId", "parentMenuId", "path", "titleKey", "iconKey", "sortOrder")
                .containsAll(fields.keySet())) throw invalid("Only menu metadata accepted");
        var item = new LinkedHashMap<String, Object>();
        item.put("menuId", text(fields.get("menuId"), 160, false));
        item.put("parentMenuId", fields.get("parentMenuId"));
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
                + " summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,capability_code,manifest_version,created_at,updated_at)"
                + " values (?,?,?,?,?,?,null,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) on"
                + " duplicate key update"
                + " parent_menu_id=values(parent_menu_id),path=values(path),title_key=values(title_key),icon_key=values(icon_key),sort_order=values(sort_order),capability_code=null,manifest_version=values(manifest_version),updated_at=CURRENT_TIMESTAMP(6)",
            item.get("menuId"),
            item.get("parentMenuId"),
            item.get("path"),
            item.get("titleKey"),
            item.get("iconKey"),
            item.get("sortOrder"),
            next);
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
      if (body.containsKey("parentMenuId")) text(body.get("parentMenuId"), 160, true);
      if (body.containsKey("sortOrder")) order(body.get("sortOrder"));
      item.putAll(body);
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
          item.get("enabled"),
          Objects.toString(item.get("parentMenuId"), ""),
          item.get("sortOrder"),
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
