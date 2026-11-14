package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Nontransactional DDL with durable checkpoints, exact writer fences and verified full backup. */
@Service
public class ControlledAuthorizationContract {
  public record Account(String username,String host) {}
  public record Approval(String format,String purpose,String operationId,String actor,String requestId,
      String targetSchema,String targetArtifactSha256,String stateFingerprint,String backupSchema,
      String backupManifestSha256,String restoreReceiptSha256,String managementReceiptSha256,
      List<Account> retiredWriters,Account newRuntime) {}

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ControlledDomainModeRecovery backup;
  private final ControlledAuthorizationCutover artifact;
  private final ControlledWriterFence accounts;

  public ControlledAuthorizationContract(JdbcTemplate db,ObjectMapper json,
      ControlledDomainModeRecovery backup,ControlledAuthorizationCutover artifact,ControlledWriterFence accounts) {
    this.db=db; this.json=json; this.backup=backup; this.artifact=artifact; this.accounts=accounts;
  }

  /** Never place DDL in an application transaction or imply it can roll back together. */
  public Map<String,Object> execute(Approval a,String approvalHash,byte[] restore,byte[] management) {
    validate(a,approvalHash);
    if(!a.targetSchema().equals(db.queryForObject("select database()",String.class))
        || !a.targetArtifactSha256().equals(artifact.actualArtifactHash()))
      throw conflict("Contract must execute on the approved schema and actual target artifact");
    var operator=accounts.operator(a.targetSchema());
    if(!operator.queryForObject("select current_user()",String.class).equals(db.queryForObject("select current_user()",String.class)))
      throw conflict("Contract datasource must use the independent approved operator identity");
    String operatorUser=operator.queryForObject("select substring_index(current_user(),'@',1)",String.class);
    if(a.retiredWriters().stream().anyMatch(account->account.username().equals(operatorUser))
        || a.newRuntime().username().equals(operatorUser))throw conflict("Runtime credentials must be separate from the operator");
    if(!hash(restore).equals(a.restoreReceiptSha256()) || !hash(management).equals(a.managementReceiptSha256()))
      throw conflict("Pinned verified backup and completed native management acceptance required");
    validateManagement(a,management);
    var prior=db.queryForList("select * from summer_authorization_contract where operation_id=?",a.operationId());
    if(prior.isEmpty() && db.queryForObject("select count(*) from summer_authorization_contract",Integer.class)!=0)
      throw conflict("Resume the original storage-contract operation; credential rotation requires a separate procedure");
    if(!prior.isEmpty() && !approvalHash.equals(prior.getFirst().get("approval_sha256")))
      throw conflict("Contract operation identity conflict");
    if(!prior.isEmpty() && "COMPLETED".equals(prior.getFirst().get("phase"))) {
      requireDropped();
      for(var writer:a.retiredWriters())readOnly(operator,writer,a.targetSchema());
      requireRuntime(operator,a.newRuntime(),a.targetSchema());
      return report(a,true);
    }
    // Check every account before the first nontransactional privilege change.
    var names=new HashSet<String>();
    for(var writer:a.retiredWriters()) {
      if(!names.add(writer.username()))throw conflict("Distinct retired writer usernames required");
      accounts.uniqueAccountHost(operator,writer.username(),writer.host());
      accounts.dedicated(accounts.grants(operator,writer.username(),writer.host()),a.targetSchema(),false);
    }
    accounts.uniqueAccountHost(operator,a.newRuntime().username(),a.newRuntime().host());
    if(names.contains(a.newRuntime().username()))throw conflict("A fresh separate runtime account is required");
    var newGrants=accounts.grants(operator,a.newRuntime().username(),a.newRuntime().host());
    if(prior.isEmpty() || !dropped())accounts.dedicated(newGrants,a.targetSchema(),true);
    else {
      accounts.dedicated(newGrants,a.targetSchema(),false);
      if(!schemaPrivileges(newGrants,a.targetSchema()).equals(Set.of("SELECT")))requireRuntime(operator,a.newRuntime(),a.targetSchema());
    }
    accounts.requireSessionInventory(operator);
    // Verify before fencing too: invalid/stale backups must not cause an avoidable outage.
    backup.verifyContractBackup(a.backupSchema(),a.stateFingerprint(),a.backupManifestSha256(),restore);
    if(prior.isEmpty())db.update("insert into summer_authorization_contract(operation_id,approval_sha256,backup_manifest_sha256,state_fingerprint,target_artifact_sha256,runtime_account,runtime_host,phase,checkpoint_json,actor,request_id) values (?,?,?,?,?,?,?,'PREPARED','{}',?,?)",
        a.operationId(),approvalHash,a.backupManifestSha256(),a.stateFingerprint(),a.targetArtifactSha256(),a.newRuntime().username(),a.newRuntime().host(),a.actor(),a.requestId());
    for(var writer:a.retiredWriters()) {
      operator.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM "+grantee(writer));
      operator.execute("GRANT SELECT ON "+MySqlSchemaPrivileges.exactObject(a.targetSchema())+" TO "+grantee(writer));
      var drained=accounts.drainRuntimeSessions(operator,writer.username());
      readOnly(operator,writer,a.targetSchema());
      checkpoint(a,"FENCED:"+writer.username(),Map.of("drainedConnectionIds",drained));
    }
    // Once old connections are drained, repeat full-domain verification before the first DROP.
    backup.verifyContractBackup(a.backupSchema(),a.stateFingerprint(),a.backupManifestSha256(),restore);
    if(exists("summer_authorization_resource_identity",null)) {
      db.execute("DROP TABLE summer_authorization_resource_identity");
      checkpoint(a,"DROP:summer_authorization_resource_identity",Map.of("completed",true));
    }
    for(String column:List.of("capability_ids","capability_codes","bundle_id"))if(exists("summer_authorization_role",column)) {
      db.execute("ALTER TABLE summer_authorization_role DROP COLUMN "+column);
      checkpoint(a,"DROP:summer_authorization_role."+column,Map.of("completed",true));
    }
    requireDropped();
    // Retired credentials stay SELECT-only forever. Only the separately provisioned new
    // account receives DML; an old still-running JVM cannot regain writer access by reconnecting.
    operator.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM "+grantee(a.newRuntime()));
    operator.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON "+MySqlSchemaPrivileges.exactObject(a.targetSchema())+" TO "+grantee(a.newRuntime()));
    requireRuntime(operator,a.newRuntime(),a.targetSchema());
    db.update("update summer_authorization_contract set phase='COMPLETED',updated_at=CURRENT_TIMESTAMP(6) where operation_id=? and approval_sha256=?",a.operationId(),approvalHash);
    return report(a,false);
  }

  private void validateManagement(Approval a,byte[] bytes) {
    var receipt=json.readTree(bytes);
    if(!"summer-managed-native-contract-acceptance/1".equals(receipt.path("format").asString())
        || !a.targetArtifactSha256().equals(receipt.path("targetArtifactSha256").asString())
        || !a.stateFingerprint().equals(receipt.path("stateFingerprint").asString()))
      throw conflict("Native management acceptance must bind the final target and current state");
    for(String scenario:List.of("groups","permissions","associations","data","roles","switchUser","revocation","sessionCsrf","tenantIsolation"))
      if(!receipt.path("scenarios").path(scenario).isBoolean() || !receipt.path("scenarios").path(scenario).asBoolean())
        throw conflict("All required native management scenarios must be accepted before destructive DDL");
  }
  private void readOnly(JdbcTemplate operator,Account account,String schema) {
    accounts.uniqueAccountHost(operator,account.username(),account.host());
    accounts.dedicated(accounts.grants(operator,account.username(),account.host()),schema,true);
  }
  private void requireRuntime(JdbcTemplate operator,Account account,String schema) {
    accounts.uniqueAccountHost(operator,account.username(),account.host());
    var grants=accounts.grants(operator,account.username(),account.host());
    accounts.dedicated(grants,schema,false);
    if(!schemaPrivileges(grants,schema).equals(Set.of("SELECT","INSERT","UPDATE","DELETE")))
      throw conflict("Exact current runtime DML privileges required");
  }
  private Set<String> schemaPrivileges(List<String> grants,String schema) {
    var privileges=new HashSet<String>();
    var pattern=java.util.regex.Pattern.compile("^GRANT (.+) ON "+java.util.regex.Pattern.quote(MySqlSchemaPrivileges.exactObject(schema))+" TO .+$");
    for(String grant:grants) {
      var match=pattern.matcher(grant);
      if(match.matches())privileges.addAll(Arrays.asList(match.group(1).split(", ")));
    }
    return privileges;
  }
  private void checkpoint(Approval a,String step,Map<String,Object> detail) {
    var encoded=db.queryForObject("select checkpoint_json from summer_authorization_contract where operation_id=?",String.class,a.operationId());
    var steps=new TreeMap<String,Object>(json.readValue(encoded,Map.class));
    steps.putIfAbsent(step,detail);
    db.update("update summer_authorization_contract set checkpoint_json=?,updated_at=CURRENT_TIMESTAMP(6) where operation_id=?",json.writeValueAsString(steps),a.operationId());
  }
  private boolean exists(String table,String column) {
    return column==null?db.queryForObject("select count(*) from information_schema.tables where table_schema=database() and table_name=?",Integer.class,table)!=0:
        db.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",Integer.class,table,column)!=0;
  }
  private boolean dropped() {return !exists("summer_authorization_resource_identity",null)
      && List.of("capability_ids","capability_codes","bundle_id").stream().noneMatch(column->exists("summer_authorization_role",column));}
  private void requireDropped() {if(!dropped())throw conflict("Physical contract incomplete; resume the exact approved operation");}
  private static String grantee(Account a) {return "'"+a.username()+"'@'"+a.host()+"'";}
  private static void validate(Approval a,String approvalHash) {
    if(a==null || !"summer-controlled-authorization-contract/1".equals(a.format())
        || !"RETIRE_LEGACY_AUTHORIZATION_STORAGE".equals(a.purpose())
        || a.actor()==null || a.actor().isBlank() || a.actor().length()>128
        || a.operationId()==null || !a.operationId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.requestId()==null || !a.requestId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.targetSchema()==null || !a.targetSchema().matches("[A-Za-z0-9_]{1,64}")
        || a.backupSchema()==null || !a.backupSchema().matches("[A-Za-z0-9_]{1,64}") || a.backupSchema().equals(a.targetSchema())
        || a.retiredWriters()==null || a.retiredWriters().isEmpty() || a.retiredWriters().size()>32 || a.newRuntime()==null)
      throw conflict("Complete separate storage-contract approval required");
    var identities=new ArrayList<>(a.retiredWriters());identities.add(a.newRuntime());
    for(var account:identities)if(account==null || account.username()==null || !account.username().matches("[A-Za-z0-9_-]{1,32}")
        || account.host()==null || !account.host().matches("[A-Za-z0-9_.%:-]{1,255}"))throw conflict("Explicit dedicated runtime accounts required");
    for(String value:List.of(Objects.toString(approvalHash,""),Objects.toString(a.targetArtifactSha256(),""),Objects.toString(a.stateFingerprint(),""),Objects.toString(a.backupManifestSha256(),""),Objects.toString(a.restoreReceiptSha256(),""),Objects.toString(a.managementReceiptSha256(),"")))
      if(!value.matches("[0-9a-f]{64}"))throw conflict("Exact native/backup/state/artifact fingerprints required");
  }
  private static String hash(byte[] bytes) {
    if(bytes==null)throw conflict("Protected contract input required");
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  private Map<String,Object> report(Approval a,boolean replay) {return Map.of("operationId",a.operationId(),"phase","COMPLETED",
      "replay",replay,"legacyStorageDropped",true,"retiredCredentialsRemainReadOnly",true,"newRuntimeAccount",a.newRuntime().username());}
  private static SummerException conflict(String message) {return SummerException.withMessage(PermissionErrorCode.CONFLICT,message);}
}
