package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Complete current grant facts and immutable mutation history; mode is bound separately. */
final class AuthorizationRecoveryFacts {
  private AuthorizationRecoveryFacts() {}

  static final List<String> DOMAIN_TABLES = List.of("summer_permission_identity", "summer_permission_group",
        "summer_permission", "summer_permission_role", "summer_role_permission",
        "summer_permission_user", "summer_permission_user_role", "summer_permission_resource",
        "summer_permission_resource_control", "summer_permission_data_scope",
        "summer_permission_selected_department", "summer_permission_bootstrap",
        "summer_permission_mutation", "summer_authorization_installation",
        "summer_authorization_cutover", "summer_authorization_mode_recovery");

  static String fingerprint(JdbcTemplate db, ObjectMapper json, String domain, long revision) {
    return LegacyAuthorizationReader.digest(json.writeValueAsString(snapshot(db,json,domain,revision)));
  }

  static Map<String,String> hashes(JdbcTemplate db, ObjectMapper json,String domain,long revision) {
    var hashes=new TreeMap<String,String>();
    snapshot(db,json,domain,revision).forEach((name,value)->hashes.put(name,
        LegacyAuthorizationReader.digest(json.writeValueAsString(value))));
    return hashes;
  }

  private static Map<String,Object> snapshot(JdbcTemplate db,ObjectMapper json,String domain,long revision) {
    var facts = new TreeMap<String, Object>();
    facts.put("format", "summer-managed-mode-recovery-facts/1");
    facts.put("domainKey", domain);
    facts.put("backupRevision", revision);
    for (String table : DOMAIN_TABLES)
      facts.put(table, rows(db, json, "select * from " + table + " where domain_key=?", domain));
    facts.put("roleStates", rows(db, json,
        "select r.role_id,r.status,r.version from summer_authorization_role r join summer_permission_role p on p.role_id=r.role_id where p.domain_key=?", domain));
    facts.put("userStates", rows(db, json,
        "select u.username,u.active from summer_auth_user u join summer_permission_user p on p.username=u.username where p.domain_key=?", domain));
    if (domain.startsWith("ENTERPRISE:"))
      facts.put("memberStates", rows(db, json,
          "select subject,status,version from summer_enterprise_member where enterprise_id=?", domain.substring(11)));
    // Decision audits and delivery retries can change while access stays denied. Immutable
    // mutation audits and event payloads must match, so a backup predating revocation fails.
    facts.put("mutationAudits", rows(db, json,
        "select * from summer_authorization_audit where enterprise_id=? and decision=''", domain));
    facts.put("mutationEvents", rows(db, json,
        "select event_id,enterprise_id,resource_type,resource_id,event_type,resource_version,request_id,payload_json,created_at from summer_authorization_change_outbox where enterprise_id=?", domain));
    return facts;
  }

  private static List<String> rows(JdbcTemplate db, ObjectMapper json, String sql, String domain) {
    return db.queryForList(sql, domain).stream()
        .map(row -> json.writeValueAsString(new TreeMap<>(row))).sorted().toList();
  }
}
