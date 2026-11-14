package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Operator-only, independently restored-backup recovery. Never seeds or changes grant facts. */
@Service
public class ControlledDomainModeRecovery {
  public record Approval(String format, String purpose, String operationId, String actor,
      String requestId, String domainKey, String targetSchema, String backupSchema,
      long backupRevision, String targetArtifactSha256, String backupManifestSha256,
      String restoreReceiptSha256, String recoveryFingerprint) {}

  private static final String PREFIX = "summer.sample.authorization.recovery.";
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ControlledAuthorizationCutover artifact;
  private final Environment environment;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;

  public ControlledDomainModeRecovery(JdbcTemplate db, ObjectMapper json,
      ControlledAuthorizationCutover artifact, Environment environment,
      AuthorizationAuditStore audit, AuthorizationChangeEventService events) {
    this.db=db; this.json=json; this.artifact=artifact; this.environment=environment;
    this.audit=audit; this.events=events;
  }

  /** Run before taking a quiescent backup. A later backup is rechecked, never assumed equal. */
  @Transactional(readOnly=true)
  public Map<String,Object> inspect(String key) {
    Domain domain=domain(key);
    long revision=healthy(db,domain);
    return Map.of("format","summer-managed-recovery-inspection/1","domainKey",key,
        "targetSchema",db.queryForObject("select database()",String.class),
        "backupRevision",revision,"targetArtifactSha256",artifact.actualArtifactHash(),
        "recoveryFingerprint",AuthorizationRecoveryFacts.fingerprint(db,json,key,revision),
        "factHashes",AuthorizationRecoveryFacts.hashes(db,json,key,revision),
        "recoveryAuthorized",false);
  }

  /** Full-domain backup anchor used only by the separately approved physical contract. */
  @Transactional(readOnly=true)
  public Map<String,Object> inspectAllManaged() {
    var states=allManagedStates(db);
    return Map.of("format","summer-managed-contract-inspection/1","domainStates",states,
        "stateFingerprint",LegacyAuthorizationReader.digest(json.writeValueAsString(states)),
        "targetSchema",db.queryForObject("select database()",String.class),
        "targetArtifactSha256",artifact.actualArtifactHash(),"contractAuthorized",false);
  }

  @Transactional(readOnly=true)
  public Map<String,Object> verifyContractBackup(String backupSchema,String expectedFingerprint,
      String manifestHash,byte[] restoredReceipt) {
    var states=allManagedStates(db);
    String fingerprint=LegacyAuthorizationReader.digest(json.writeValueAsString(states));
    String source=db.queryForObject("select database()",String.class);
    var receipt=json.readTree(restoredReceipt);
    if(!fingerprint.equals(expectedFingerprint) || source.equals(backupSchema)
        || !"summer-verified-managed-contract-restore/1".equals(receipt.path("format").asString())
        || !receipt.path("verified").isBoolean() || !receipt.path("verified").asBoolean()
        || !source.equals(receipt.path("sourceSchema").asString())
        || !backupSchema.equals(receipt.path("targetSchema").asString())
        || !manifestHash.equals(receipt.path("backupManifestSha256").asString())
        || !fingerprint.equals(receipt.path("sourceFingerprint").asString())
        || !artifact.actualArtifactHash().equals(receipt.path("bridgeSha256").asString())
        || !states.containsKey(receipt.path("domainKey").asString()))
      throw conflict("Complete source-bound authenticated contract backup required");
    try(var connection=backupConnection()) {
      connection.setReadOnly(true);
      var backup=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
      if(!backupSchema.equals(backup.queryForObject("select database()",String.class)))
        throw conflict("Independent contract backup schema mismatch");
      requireBackupReader(backup,backupSchema);
      if(!states.equals(allManagedStates(backup)))
        throw conflict("Every current managed domain and mutation history must match the authenticated backup");
    } catch(java.sql.SQLException unavailable) {throw conflict("Independent contract backup unavailable", unavailable);}
    return Map.of("stateFingerprint",fingerprint,"domainStates",states,"backupManifestSha256",manifestHash,"verified",true);
  }

  private Map<String,Object> allManagedStates(JdbcTemplate source) {
    var states=new TreeMap<String,Object>();
    for(String key:source.queryForList("select domain_key from summer_authorization_domain order by domain_key",String.class)) {
      var domain=domain(key);long revision=healthy(source,domain);
      var state=new TreeMap<String,Object>();
      state.put("revision",revision);
      state.put("factsFingerprint",AuthorizationRecoveryFacts.fingerprint(source,json,key,revision));
      states.put(key,state);
    }
    if(states.isEmpty())throw conflict("At least one explicit managed domain is required for contract");
    var domainTables=new ArrayList<>(AuthorizationRecoveryFacts.DOMAIN_TABLES);
    domainTables.addAll(List.of("summer_authorization_bridge_enrollment",
        "summer_authorization_migration_snapshot","summer_authorization_writer_fence"));
    for(String table:domainTables)
      for(String key:source.queryForList("select distinct domain_key from "+table,String.class))
        if(!states.containsKey(key))throw conflict("Unregistered managed grant/history domain forbids global contract");
    // Old rows with an unregistered domain cannot be silently discarded by global DDL.
    for(String table:List.of("summer_authorization_user_binding","summer_data_scope_policy",
        "summer_data_scope_binding","summer_data_scope_policy_audit"))
      for(String id:source.queryForList("select distinct enterprise_id from "+table,String.class))
        if(!states.containsKey("ENTERPRISE:"+id))throw conflict("Unresolved legacy binding/DATA domain forbids global contract");
    for(String table:List.of("summer_authorization_audit","summer_authorization_change_outbox"))
      for(String owner:source.queryForList("select distinct enterprise_id from "+table
          +(table.equals("summer_authorization_audit")?" where decision=''":""),String.class)) {
        String key=owner!=null && owner.contains(":")?owner:"ENTERPRISE:"+owner;
        if(!states.containsKey(key))throw conflict("Unresolved authorization audit/event domain forbids global contract");
      }
    for(String id:source.queryForList("select distinct r.enterprise_id from summer_authorization_role r where not exists(select 1 from summer_permission_role p where p.role_id=r.role_id)",String.class))
      if(!states.containsKey("ENTERPRISE:"+id))throw conflict("Unresolved legacy role domain forbids global contract");
    for(var capability:source.queryForList("select distinct enterprise_id,scope from summer_authorization_capability")) {
      Object owner=capability.get("enterprise_id");
      String key="PLATFORM".equals(capability.get("scope"))?"PLATFORM:platform":owner==null?null:"ENTERPRISE:"+owner;
      if(key==null || !states.containsKey(key))throw conflict("Unresolved legacy capability domain forbids global contract");
    }
    return states;
  }

  @Transactional
  public Map<String,Object> execute(Approval a,String approvalHash,byte[] restoredReceipt) {
    validate(a,approvalHash);
    Domain domain=domain(a.domainKey());
    if(!a.targetSchema().equals(db.queryForObject("select database()",String.class))
        || !a.targetArtifactSha256().equals(artifact.actualArtifactHash()))
      throw conflict("Recovery must bind the actual primary schema and executing artifact");
    var prior=db.queryForList("select * from summer_authorization_mode_recovery where operation_id=? for update",a.operationId());
    if(!prior.isEmpty()) {
      var receipt=prior.getFirst();
      if(!a.domainKey().equals(receipt.get("domain_key")) || !approvalHash.equals(receipt.get("approval_sha256"))
          || healthy(db,domain)<((Number)receipt.get("committed_revision")).longValue())
        throw conflict("Recovery replay conflict; it cannot repair another missing mode");
      return report(receipt);
    }
    if(!db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update",a.domainKey()).isEmpty())
      throw conflict("Recovery is only for an absent mode; existing or corrupt states require separate investigation");
    if(restoredReceipt==null || !hash(restoredReceipt).equals(a.restoreReceiptSha256()))
      throw conflict("Pinned authenticated isolated recovery receipt required");
    var receipt=json.readTree(restoredReceipt);
    if(!receipt.path("format").asString().equals("summer-verified-managed-recovery-restore/1")
        || !receipt.path("verified").isBoolean() || !receipt.path("verified").asBoolean()
        || !receipt.path("domainKey").asString().equals(a.domainKey())
        || !receipt.path("sourceSchema").asString().equals(a.targetSchema())
        || !receipt.path("targetSchema").asString().equals(a.backupSchema())
        || !receipt.path("backupManifestSha256").asString().equals(a.backupManifestSha256())
        || !receipt.path("sourceFingerprint").asString().equals(a.recoveryFingerprint())
        || !receipt.path("bridgeSha256").asString().equals(a.targetArtifactSha256())
        || !receipt.path("restoredModeRevision").asString().equals("MANAGED:"+a.backupRevision()))
      throw conflict("Verified recovery restore binding mismatch");
    // Credentials arrive only through process environment/configuration, never approval JSON.
    try(var connection=backupConnection()) {
      connection.setReadOnly(true);
      var backup=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
      if(!a.backupSchema().equals(backup.queryForObject("select database()",String.class)))
        throw conflict("Independent backup connection schema mismatch");
      requireBackupReader(backup,a.backupSchema());
      if(healthy(backup,domain)!=a.backupRevision())throw conflict("Backup revision differs from the approved healthy checkpoint");
      var backupHashes=AuthorizationRecoveryFacts.hashes(backup,json,a.domainKey(),a.backupRevision());
      var currentHashes=AuthorizationRecoveryFacts.hashes(db,json,a.domainKey(),a.backupRevision());
      var changed=backupHashes.keySet().stream().filter(name->!backupHashes.get(name).equals(currentHashes.get(name))).toList();
      if(!changed.isEmpty())throw conflict("Current grants/history differ from backup in "+changed+"; recovery cannot reset revocations");
      if(!AuthorizationRecoveryFacts.fingerprint(backup,json,a.domainKey(),a.backupRevision()).equals(a.recoveryFingerprint()))
        throw conflict("Backup grants/history differ from approved inspection fingerprint");
    } catch(java.sql.SQLException unavailable) {
      throw conflict("Independent verified backup could not be read", unavailable);
    }
    // All grant, identity, provenance and immutable mutation rows remain untouched.
    db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) values (?,'MANAGED',?)",a.domainKey(),a.backupRevision());
    audit.mutation(a.domainKey(),a.operationId(),"AUTHORIZATION_MODE_RECOVERED",a.requestId(),a.actor());
    events.enqueue(domain,"RECOVERY",a.operationId(),"AUTHORIZATION_MODE_RECOVERED",1,a.requestId());
    long revision=db.queryForObject("select revision from summer_authorization_domain where domain_key=?",Long.class,a.domainKey());
    if(revision<=a.backupRevision())throw conflict("Recovery must advance revision monotonically");
    db.update("insert into summer_authorization_mode_recovery(operation_id,domain_key,approval_sha256,backup_manifest_sha256,recovery_fingerprint,target_artifact_sha256,backup_revision,committed_revision,actor,request_id) values (?,?,?,?,?,?,?,?,?,?)",
        a.operationId(),a.domainKey(),approvalHash,a.backupManifestSha256(),a.recoveryFingerprint(),a.targetArtifactSha256(),a.backupRevision(),revision,a.actor(),a.requestId());
    return report(db.queryForMap("select * from summer_authorization_mode_recovery where operation_id=?",a.operationId()));
  }

  private void requireBackupReader(JdbcTemplate backup,String schema) {
    var grants=backup.queryForList("show grants",String.class);
    boolean select=false;
    for(String grant:grants) {
      if(grant.matches("GRANT USAGE ON \\*\\.\\* TO .+") && !grant.contains("WITH GRANT OPTION"))continue;
      if(!grant.startsWith("GRANT SELECT ON "+MySqlSchemaPrivileges.exactObject(schema)+" TO ") || grant.contains("WITH GRANT OPTION"))
        throw conflict("Backup reader must have SELECT only on the isolated backup and no roles/proxy/global privileges");
      select=true;
    }
    if(!select)throw conflict("Independent isolated backup SELECT grant required");
  }

  private java.sql.Connection backupConnection() throws java.sql.SQLException {
    var properties=new Properties();
    properties.setProperty("user",environment.getRequiredProperty(PREFIX+"backup-user"));
    properties.setProperty("password",environment.getRequiredProperty(PREFIX+"backup-password"));
    // Match the primary Hikari UTC session, including TIMESTAMP interpretation.
    properties.setProperty("connectionTimeZone","UTC");
    properties.setProperty("forceConnectionTimeZoneToSession","true");
    properties.setProperty("preserveInstants","true");
    return DriverManager.getConnection(environment.getRequiredProperty(PREFIX+"backup-url"),properties);
  }

  private long healthy(JdbcTemplate source,Domain domain) {
    var state=source.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=?",domain.key());
    if(state.size()!=1 || !"MANAGED".equals(state.getFirst().get("engine_mode"))
        || !(state.getFirst().get("revision") instanceof Number revision) || revision.longValue()<0
        || source.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?",Integer.class,domain.key())!=1
        || source.queryForObject("select (select count(*) from summer_authorization_installation where domain_key=?)+(select count(*) from summer_authorization_cutover where domain_key=? and purpose='PRODUCTION_CUTOVER')",Integer.class,domain.key(),domain.key())!=1)
      throw conflict("Complete managed provenance, marker and valid revision required");
    return revision.longValue();
  }

  private static Domain domain(String key) {
    if("PLATFORM:platform".equals(key))return Domain.platform();
    if(key!=null && key.matches("ENTERPRISE:[A-Za-z0-9_-]{1,80}"))return Domain.enterprise(key.substring(11));
    throw conflict("Exact full Domain required for recovery");
  }

  private static void validate(Approval a,String hash) {
    if(a==null || !"summer-controlled-mode-recovery/1".equals(a.format())
        || !"RECOVER_MANAGED_DOMAIN_MODE_ONLY".equals(a.purpose()) || a.backupRevision()<0
        || a.actor()==null || a.actor().isBlank() || a.actor().length()>128
        || a.operationId()==null || !a.operationId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.requestId()==null || !a.requestId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.targetSchema()==null || !a.targetSchema().matches("[A-Za-z0-9_]{1,64}")
        || a.backupSchema()==null || !a.backupSchema().matches("[A-Za-z0-9_]{1,64}") || a.targetSchema().equals(a.backupSchema()))
      throw conflict("Complete explicit mode recovery approval required");
    for(String value:List.of(Objects.toString(hash,""),Objects.toString(a.targetArtifactSha256(),""),
        Objects.toString(a.backupManifestSha256(),""),Objects.toString(a.restoreReceiptSha256(),""),Objects.toString(a.recoveryFingerprint(),"")))
      if(!value.matches("[0-9a-f]{64}"))throw conflict("Exact mode recovery fingerprints required");
  }

  private static String hash(byte[] bytes) {
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  private Map<String,Object> report(Map<String,Object> receipt) {
    return Map.of("operationId",receipt.get("operation_id"),"domainKey",receipt.get("domain_key"),
        "committedRevision",receipt.get("committed_revision"),"modeRecovered",true,"grantsRecreated",false);
  }
  private static SummerException conflict(String message) {return SummerException.withMessage(PermissionErrorCode.CONFLICT,message);}
  private static SummerException conflict(String message, Throwable cause) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message, cause);
  }

}
