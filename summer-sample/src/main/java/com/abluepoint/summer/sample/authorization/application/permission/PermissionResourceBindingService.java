package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One collection version for each permission/type, independent of discovery and definition
 * versions.
 */
@Service
public class PermissionResourceBindingService {
  private final JdbcTemplate db;
  private final tools.jackson.databind.ObjectMapper json;
  private final com.abluepoint.summer.sample.authorization.AuthorizationAuditStore audit;
  private final com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService events;
  private final PermissionResourceAssociations associations;
  private final Map<String, TrustedPermissionResourceResolver> resolvers;

  public PermissionResourceBindingService(
      JdbcTemplate db,
      tools.jackson.databind.ObjectMapper json,
      com.abluepoint.summer.sample.authorization.AuthorizationAuditStore audit,
      com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService events,
      PermissionResourceAssociations associations,
      List<TrustedPermissionResourceResolver> resolvers) {
    this.db = db;
    this.json = json;
    this.audit = audit;
    this.events = events;
    this.associations = associations;
    this.resolvers =
        resolvers.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    TrustedPermissionResourceResolver::type,
                    java.util.function.Function.identity()));
  }

  private void permission(Domain d, String id, String type) {
    if (!Set.of("INTERFACE", "MENU").contains(type))
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "Unsupported resource type");
    var rows =
        db.queryForList(
            "select type from summer_permission where domain_key=? and permission_id=?",
            d.key(),
            id);
    if (rows.isEmpty())
      throw SummerException.withMessage(PermissionErrorCode.NOT_FOUND, "Permission not found");
    if (!type.equals(rows.getFirst().get("type")))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID, "Resource type differs from permission");
  }

  @Transactional(readOnly = true)
  public PermissionService.Result read(Domain d, String id, String type) {
    permission(d, id, type);
    var versions =
        db.queryForList(
            "select version from summer_permission_resource_control where domain_key=? and"
                + " resource_type=? and control_kind='COLLECTION' and target_id=?",
            Long.class,
            d.key(),
            type,
            id);
    long version = versions.isEmpty() ? 0 : versions.getFirst();
    var ids =
        db.queryForList(
            "select resource_id from summer_permission_resource where domain_key=? and"
                + " permission_id=? and resource_type=? order by resource_id",
            String.class,
            d.key(),
            id,
            type);
    return new PermissionService.Result(
        Map.of("permissionId", id, "resourceType", type, "resourceIds", ids, "version", version),
        200,
        version);
  }

  private static SummerException fail(PermissionErrorCode code, String message) {
    return SummerException.withMessage(code, message);
  }

  private void control(Domain d, String type, String kind, String id) {
    db.update(
        "insert into summer_permission_resource_control values (?,?,?,?,0) on duplicate key update"
            + " target_id=target_id",
        d.key(),
        type,
        kind,
        id);
    db.queryForList(
        "select version from summer_permission_resource_control where domain_key=? and"
            + " resource_type=? and control_kind=? and target_id=? for update",
        d.key(),
        type,
        kind,
        id);
  }

  private void advance(Domain d, String type, String kind, String id) {
    db.update(
        "update summer_permission_resource_control set version=version+1 where domain_key=? and"
            + " resource_type=? and control_kind=? and target_id=?",
        d.key(),
        type,
        kind,
        id);
  }

  @Transactional
  public PermissionService.Result replace(
      Domain d,
      String actor,
      String id,
      String type,
      List<String> resourceIds,
      String ifMatch,
      String key,
      String requestId) {
    if (!Set.of("INTERFACE", "MENU").contains(type)
        || resourceIds == null
        || resourceIds.size() > 10000
        || resourceIds.stream()
            .anyMatch(
                x -> x == null || x.isBlank() || x.length() > ("MENU".equals(type) ? 160 : 96)))
      throw fail(PermissionErrorCode.INVALID, "An explicit resource ID list is required");
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw fail(PermissionErrorCode.INVALID, "Idempotency-Key required");
    var ids = resourceIds.stream().distinct().sorted().toList();
    var locked =
        db.queryForList(
            "select engine_mode from summer_authorization_domain where domain_key=? for update",
            d.key());
    if (locked.isEmpty() || !"MANAGED".equals(locked.getFirst().get("engine_mode")))
      throw fail(PermissionErrorCode.CONFLICT, "Managed domain required");
    String operation = "resource-bindings:" + type + ":" + id;
    String fingerprint;
    try {
      fingerprint =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(
                          json.writeValueAsString(Arrays.asList(type, id, ids, ifMatch))
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    var receipts =
        db.queryForList(
            "select * from summer_permission_mutation where domain_key=? and actor=? and"
                + " operation_key=? and idempotency_key=?",
            d.key(),
            actor,
            operation,
            key);
    if (!receipts.isEmpty()) {
      var receipt = receipts.getFirst();
      if (!fingerprint.equals(receipt.get("fingerprint")))
        throw fail(PermissionErrorCode.CONFLICT, "Idempotency key reused");
      return new PermissionService.Result(
          json.readValue(receipt.get("response_json").toString(), Map.class),
          ((Number) receipt.get("response_status")).intValue(),
          ((Number) receipt.get("response_version")).longValue());
    }
    permission(d, id, type);
    control(d, type, "COLLECTION", id);
    var resolver = resolvers.get(type);
    if (resolver == null && !ids.isEmpty())
      throw fail(PermissionErrorCode.INVALID, "Trusted resource adapter required");
    if (resolver != null) resolver.prepare(d);
    var current = read(d, id, type);
    if (ifMatch == null || ifMatch.isBlank())
      throw SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.PERMISSION_CONDITION_REQUIRED,
          "If-Match is required");
    if (!ifMatch.matches("\"[0-9]+\"")) throw fail(PermissionErrorCode.INVALID, "Invalid If-Match");
    if (!ifMatch.equals("\"" + current.version() + "\""))
      throw SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.PERMISSION_STALE,
          "The association collection has changed");
    var old =
        db.queryForList(
            "select resource_id from summer_permission_resource where domain_key=? and"
                + " permission_id=? and resource_type=? order by resource_id",
            String.class,
            d.key(),
            id,
            type);
    var affected = new TreeSet<>(old);
    affected.addAll(ids);
    for (String resource : affected) control(d, type, "RESOURCE", resource);
    db.queryForList(
        "select permission_id from summer_permission where domain_key=? and permission_id=? for"
            + " update",
        d.key(),
        id);
    var resolved = ids.stream().map(value -> resolver.resolve(d, value)).toList();
    for (int i = 0; i < resolved.size(); i++)
      if (!ids.get(i).equals(resolved.get(i).id()) || !type.equals(resolved.get(i).type()))
        throw fail(PermissionErrorCode.INVALID, "Invalid trusted resource identity");
    db.update(
        "delete from summer_permission_resource where domain_key=? and permission_id=? and"
            + " resource_type=?",
        d.key(),
        id,
        type);
    for (var resource : resolved) associations.bind(d, id, resource);
    advance(d, type, "COLLECTION", id);
    for (String resource : affected) advance(d, type, "RESOURCE", resource);
    var result = read(d, id, type);
    audit.mutation(d.key(), id, "PERMISSION_" + type + "_BINDINGS", requestId, actor);
    events.enqueue(
        d, "PERMISSION", id, "PERMISSION_RESOURCE_BINDINGS_CHANGED", result.version(), requestId);
    db.update(
        "insert into summer_permission_mutation values (?,?,?,?,?,?,?,?)",
        d.key(),
        actor,
        operation,
        key,
        fingerprint,
        json.writeValueAsString(result.body()),
        result.status(),
        result.version());
    return result;
  }
}
