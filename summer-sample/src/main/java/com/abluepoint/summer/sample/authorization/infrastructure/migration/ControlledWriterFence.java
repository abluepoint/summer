package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Dedicated old-account isolation; explicitly handles existing pooled MySQL privileges. */
@Service
public class ControlledWriterFence {
  public record Approval(String format, String purpose, String fenceId, String actor,
      String snapshotId, String sourceFingerprint, String bridgeSha256,
      String enrollmentApprovalSha256, String schemaFingerprint, String sourceSchema,
      String oldAccount, String oldHost, String executionReceiptSha256) {}
  private static final String PREFIX = "summer.sample.authorization.upgrade.operator-";
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final Environment environment;
  private final AuthorizationMigrationService migration;

  public ControlledWriterFence(JdbcTemplate db, ObjectMapper json, Environment environment,
      AuthorizationMigrationService migration) {
    this.db = db; this.json = json; this.environment = environment; this.migration = migration;
  }

  /** Not transactional: REVOKE and session termination cannot be rolled back by application DML. */
  public Map<String,Object> fence(Approval approval, String approvalHash, Path executionReceipt) throws Exception {
    validate(approval, approvalHash);
    var input = migration.readFrozenInput(approval.snapshotId());
    if (!approval.sourceFingerprint().equals(input.sourceFingerprint())) throw conflict("Frozen source approval mismatch");
    if (!approval.sourceSchema().equals(db.queryForObject("select database()", String.class)))
      throw conflict("Fence must execute against the approved source schema");
    var enrollment = db.queryForList("select bridge_sha256,schema_fingerprint,approval_sha256 from summer_authorization_bridge_enrollment where domain_key=?",input.domain().key());
    if (enrollment.size()!=1 || !approval.bridgeSha256().equals(enrollment.getFirst().get("bridge_sha256"))
        || !approval.schemaFingerprint().equals(enrollment.getFirst().get("schema_fingerprint"))
        || !approval.enrollmentApprovalSha256().equals(enrollment.getFirst().get("approval_sha256")))
      throw conflict("Exact source bridge enrollment required");
    var prior=db.queryForList("select approval_sha256,phase from summer_authorization_writer_fence where fence_id=?",approval.fenceId());
    if (!prior.isEmpty() && !approvalHash.equals(prior.getFirst().get("approval_sha256")))
      throw conflict("Fence operation identity conflict");
    // Only the first attempt must observe the original process alive. A persisted checkpoint
    // already pins that observation; exact resume/replay must also work after that JVM stops.
    validateExecution(approval, executionReceipt, input.evidenceJson(),prior.isEmpty());
    if(!prior.isEmpty() && "FENCED".equals(prior.getFirst().get("phase"))) {
      var receipt=requireSnapshot(input.snapshotId());
      return Map.of("fenceId",approval.fenceId(),"phase","FENCED",
          "checkpoint",json.readTree(receipt.get("checkpoint_json").toString()),
          "grantsHash",receipt.get("grants_hash"),"cutoverAuthorized",false);
    }
    var operator = operator(approval.sourceSchema());
    uniqueAccountHost(operator,approval.oldAccount(),approval.oldHost());
    if (operator.queryForObject("select substring_index(current_user(),'@',1)",String.class).equals(approval.oldAccount()))
      throw conflict("Operator cannot use the old runtime account");
    // Refuse shared identities before changing any privilege: only this schema's direct grant
    // and USAGE are permitted. Role/proxy/global/other-schema grants are not auto-removed.
    var grants=grants(operator,approval.oldAccount(),approval.oldHost());
    dedicated(grants,approval.sourceSchema(),false);
    String grantee="'"+approval.oldAccount()+"'@'"+approval.oldHost()+"'";
    if (operator.queryForObject("select count(*) from mysql.role_edges where TO_USER=? and TO_HOST=?",Integer.class,approval.oldAccount(),approval.oldHost())!=0)
      throw conflict("Inherited old-account roles require a separate approved recipe");
    requireSessionInventory(operator);
    if (prior.isEmpty()) db.update("insert into summer_authorization_writer_fence(fence_id,snapshot_id,domain_key,source_fingerprint,source_schema,old_account,old_host,approval_sha256,execution_receipt_sha256,phase,checkpoint_json,actor) values (?,?,?,?,?,?,?,?,?,'FENCING','{}',?)",
        approval.fenceId(),input.snapshotId(),input.domain().key(),input.sourceFingerprint(),approval.sourceSchema(),approval.oldAccount(),approval.oldHost(),approvalHash,approval.executionReceiptSha256(),approval.actor());
    operator.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM "+grantee);
    operator.execute("GRANT SELECT ON "+MySqlSchemaPrivileges.exactObject(approval.sourceSchema())+" TO "+grantee);
    var drained=drainRuntimeSessions(operator,approval.oldAccount());
    grants=grants(operator,approval.oldAccount(),approval.oldHost());
    dedicated(grants,approval.sourceSchema(),true);
    var checkpoint=new TreeMap<String,Object>();
    checkpoint.put("oldProcessStillAlive",ProcessHandle.of(json.readTree(Files.readString(executionReceipt)).path("pid").asLong()).map(ProcessHandle::isAlive).orElse(false));
    checkpoint.put("drainedConnectionIds",drained);checkpoint.put("grants",grants);
    checkpoint.put("sourceFingerprint",input.sourceFingerprint());checkpoint.put("cutoverAuthorized",false);
    String hash=LegacyAuthorizationReader.digest(json.writeValueAsString(grants));
    db.update("update summer_authorization_writer_fence set phase='FENCED',grants_hash=?,checkpoint_json=? where fence_id=? and approval_sha256=?",hash,json.writeValueAsString(checkpoint),approval.fenceId(),approvalHash);
    return Map.of("fenceId",approval.fenceId(),"phase","FENCED","checkpoint",checkpoint,"grantsHash",hash,"cutoverAuthorized",false);
  }

  /** Revalidate actual source privileges at every subsequent operator stage/cutover boundary. */
  public Map<String,Object> requireSnapshot(String snapshotId) {
    var input=migration.readFrozenInput(snapshotId);
    var receipts=db.queryForList("select * from summer_authorization_writer_fence where snapshot_id=? and domain_key=?",snapshotId,input.domain().key());
    if (receipts.size()!=1 || !"FENCED".equals(receipts.getFirst().get("phase"))
        || !input.sourceFingerprint().equals(receipts.getFirst().get("source_fingerprint")))
      throw conflict("Completed source-bound old writer fence required");
    var receipt=receipts.getFirst();
    String sourceSchema=receipt.get("source_schema").toString();
    var operator=operator(sourceSchema);
    uniqueAccountHost(operator,receipt.get("old_account").toString(),receipt.get("old_host").toString());
    var current=grants(operator,receipt.get("old_account").toString(),receipt.get("old_host").toString());
    dedicated(current,sourceSchema,true);
    if (!LegacyAuthorizationReader.digest(json.writeValueAsString(current)).equals(receipt.get("grants_hash")))
      throw conflict("Old writer privileges changed after fencing");
    var source=operator.queryForList("select approval_sha256,phase,grants_hash,source_fingerprint from summer_authorization_writer_fence where fence_id=?",receipt.get("fence_id"));
    if (source.size()!=1 || !"FENCED".equals(source.getFirst().get("phase"))
        || !Objects.equals(source.getFirst().get("approval_sha256"),receipt.get("approval_sha256"))
        || !Objects.equals(source.getFirst().get("grants_hash"),receipt.get("grants_hash"))
        || !input.sourceFingerprint().equals(source.getFirst().get("source_fingerprint")))
      throw conflict("Actual source fence no longer matches restored ledger");
    return Collections.unmodifiableMap(receipt);
  }

  JdbcTemplate operator(String schema) {
    var source=new DriverManagerDataSource(environment.getRequiredProperty(PREFIX+"url"),
        environment.getRequiredProperty(PREFIX+"username"),environment.getRequiredProperty(PREFIX+"password"));
    var operator=new JdbcTemplate(source);
    if (!schema.equals(operator.queryForObject("select database()",String.class)))
      throw conflict("Operator connection is outside the approved source schema");
    return operator;
  }

  List<String> grants(JdbcTemplate operator,String account,String host) {
    if (!account.matches("[A-Za-z0-9_-]{1,32}") || !host.matches("[A-Za-z0-9_.%:-]{1,255}"))
      throw conflict("Explicit old account and host required");
    return operator.queryForList("SHOW GRANTS FOR '"+account+"'@'"+host+"'",String.class).stream().sorted().toList();
  }

  void uniqueAccountHost(JdbcTemplate operator,String account,String host) {
    var hosts=operator.queryForList("select Host from mysql.user where User=? order by Host",String.class,account);
    if(!hosts.equals(List.of(host)))
      throw conflict("Old username must have exactly the approved host identity; no privileges changed");
  }

  void requireSessionInventory(JdbcTemplate operator) {
    // INFORMATION_SCHEMA.PROCESSLIST temporarily reports a routine/trigger DEFINER as USER.
    // The Performance Schema foreground identity remains the authenticated connection owner.
    try {
      if(operator.queryForObject("select count(*) from performance_schema.threads where PROCESSLIST_ID=CONNECTION_ID() and TYPE='FOREGROUND'",Integer.class)!=1)
        throw conflict("Stable authenticated connection inventory required before fencing");
    } catch(org.springframework.dao.DataAccessException unavailable) {
      throw conflict("Stable authenticated connection inventory unavailable; no privileges changed", unavailable);
    }
  }

  /** Call only after unique host and single-schema dedicated-account validation. */
  List<Long> drainRuntimeSessions(JdbcTemplate operator,String account) {
    var connections=operator.queryForList("select PROCESSLIST_ID from performance_schema.threads where TYPE='FOREGROUND' and PROCESSLIST_USER=? and PROCESSLIST_ID is not null order by PROCESSLIST_ID",Long.class,account);
    var drained=new ArrayList<Long>();
    for(Long connection:connections) {
      if(ownedSession(operator,connection,account)==1) {
        try {operator.execute("KILL CONNECTION "+connection);}
        catch(org.springframework.dao.DataAccessException unavailable) {
          if(ownedSession(operator,connection,account)!=0)throw conflict("Owned authenticated session drain incomplete; resume the approved operation", unavailable);
        }
        drained.add(connection);
      }
    }
    for(Long connection:connections)if(ownedSession(operator,connection,account)!=0)
      throw conflict("Owned authenticated session remains; resume the approved operation");
    return drained;
  }

  private int ownedSession(JdbcTemplate operator,long id,String account) {
    return operator.queryForObject("select count(*) from performance_schema.threads where TYPE='FOREGROUND' and PROCESSLIST_ID=? and PROCESSLIST_USER=?",Integer.class,id,account);
  }

  void dedicated(List<String> grants,String schema,boolean readOnly) {
    if (grants.isEmpty()) throw conflict("Actual old-account grants unavailable");
    for (String grant:grants) {
      if (grant.contains("WITH GRANT OPTION")) throw conflict("Old account is not a dedicated runtime identity");
      var match=java.util.regex.Pattern.compile("^GRANT (.+) ON (.+) TO .+$").matcher(grant);
      if (!match.matches()) throw conflict("Inherited/proxy account grants are unsupported");
      String privileges=match.group(1),object=match.group(2);
      if (object.equals("*.*") && privileges.equals("USAGE")) continue;
      if (!object.equals(MySqlSchemaPrivileges.exactObject(schema)) || readOnly && !privileges.equals("SELECT"))
        throw conflict("Old account has shared or writable privileges");
    }
  }

  private void validateExecution(Approval approval,Path receipt,String evidenceJson,boolean requireLiveProcess) throws Exception {
    if (!Files.isRegularFile(receipt,LinkOption.NOFOLLOW_LINKS) || Files.size(receipt)>8*1024*1024)
      throw conflict("Pinned actual bridge execution receipt required");
    byte[] bytes=Files.readAllBytes(receipt);
    if (!hash(bytes).equals(approval.executionReceiptSha256())) throw conflict("Execution receipt hash mismatch");
    var execution=json.readTree(bytes);
    if (!"summer-actual-bridge-execution/1".equals(execution.path("format").asString())
        || !approval.bridgeSha256().equals(execution.path("artifactSha256").asString())
        || !approval.sourceFingerprint().equals(execution.path("operatorSourceFingerprint").asString())
        || !approval.schemaFingerprint().equals(execution.path("operatorSchemaFingerprint").asString()))
      throw conflict("Actual old execution/source binding mismatch");
    boolean allowed=false;
    try(var stream=new ClassPathResource("authorization/trusted-upgrade-bridges.json").getInputStream()) {
      for(var bridge:json.readTree(stream).path("bridges"))
        if(approval.bridgeSha256().equals(bridge.path("artifactSha256").asString()))allowed=true;
    }
    if(!allowed)throw conflict("Unknown actual old build");
    Path artifact=Path.of(execution.path("actualArtifact").asString()).toRealPath();
    if(!hash(Files.readAllBytes(artifact)).equals(approval.bridgeSha256()))
      throw conflict("Pinned old artifact changed since execution");
    if(requireLiveProcess) {
      long pid=execution.path("pid").asLong();
      var process=ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElseThrow(()->conflict("Actual old JVM is not alive"));
      var arguments=process.info().arguments().orElseThrow(()->conflict("Actual old JVM arguments unavailable"));
      var args=List.of(arguments);int index=args.indexOf("-jar");
      if(index<0 || index+2!=args.size() || !Path.of(args.get(index+1)).toRealPath().equals(artifact))
        throw conflict("Running old JVM does not use the approved artifact");
    }
    var captured=json.readTree(evidenceJson).path("dataSemanticsClassHashes");
    try(var jar=new JarFile(artifact.toFile())) {
      for(var entry:captured.properties()) {
        String name=entry.getKey().replace('.','/')+".class";
        var item=jar.getJarEntry("BOOT-INF/classes/"+name);
        if(item==null)throw conflict("Old execution semantics class unavailable");
        try(var stream=jar.getInputStream(item)) {
          if(!hash(stream.readAllBytes()).equals(entry.getValue().asString())
              || !entry.getValue().asString().equals(execution.path("installedClassHashes").path(name).asString()))
            throw conflict("Captured old semantics differ from the actual running artifact");
        }
      }
    }
  }

  private void validate(Approval approval,String hash) {
    if(approval==null || !"summer-old-writer-fence/1".equals(approval.format())
        || !"FENCE_DEDICATED_OLD_ACCOUNT".equals(approval.purpose())
        || approval.actor()==null || approval.actor().isBlank() || approval.actor().length()>128
        || approval.fenceId()==null || !approval.fenceId().matches("[A-Za-z0-9._:-]{1,128}")
        || approval.sourceSchema()==null || !approval.sourceSchema().matches("[A-Za-z0-9_]{1,64}")
        || approval.oldAccount()==null || !approval.oldAccount().matches("[A-Za-z0-9_-]{1,32}")
        || approval.oldHost()==null || !approval.oldHost().matches("[A-Za-z0-9_.%:-]{1,255}"))
      throw conflict("Complete dedicated old-writer approval required");
    for(String value:List.of(Objects.toString(hash,""),Objects.toString(approval.sourceFingerprint(),""),
        Objects.toString(approval.bridgeSha256(),""),Objects.toString(approval.enrollmentApprovalSha256(),""),
        Objects.toString(approval.schemaFingerprint(),""),Objects.toString(approval.executionReceiptSha256(),"")))
      if(!value.matches("[0-9a-f]{64}"))throw conflict("Exact fence provenance hashes required");
  }
  private String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  private static SummerException conflict(String message) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT,message);
  }
  private static SummerException conflict(String message, Throwable cause) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message, cause);
  }

}
