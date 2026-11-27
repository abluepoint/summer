package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.application.migration.RuntimeMigrationResolution;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Explicit local cutover of one frozen enterprise; ordinary startup never calls this boundary. */
@Service
public class ControlledAuthorizationCutover {
  public record Approval(String format, String purpose, String operationId, String actor,
      String requestId, String snapshotId, String sourceFingerprint, long expectedRevision,
      String targetSchema, String targetArtifactSha256, String authorizationFingerprint,
      String restoreReceiptSha256, String planHash, String verificationSha256,
      String comparisonInputsSha256) {}

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthorizationMigrationService migration;
  private final AuthorizationDataMigrationService data;
  private final RuntimeMigrationResolution runtime;
  private final ControlledWriterFence fence;
  private final LegacyAuthorizationReader legacy;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;
  private final List<com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver> resolvers;

  public ControlledAuthorizationCutover(JdbcTemplate db, ObjectMapper json,
      AuthorizationMigrationService migration, AuthorizationDataMigrationService data,
      RuntimeMigrationResolution runtime, ControlledWriterFence fence, LegacyAuthorizationReader legacy,
      AuthorizationAuditStore audit, AuthorizationChangeEventService events,
      List<com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver> resolvers) {
    this.db=db; this.json=json; this.migration=migration; this.data=data; this.runtime=runtime;
    this.fence=fence; this.legacy=legacy; this.audit=audit; this.events=events;
    this.resolvers=List.copyOf(resolvers);
  }

  /** Deterministic current facts for the operator's approval, excluding mutable delivery state. */
  @Transactional(readOnly=true)
  public Map<String,Object> inspect(String snapshotId) {
    var input=migration.readFrozenInput(snapshotId);
    return Map.of("domainKey",input.domain().key(),"sourceFingerprint",input.sourceFingerprint(),
        "expectedRevision",input.expectedRevision(),"targetSchema",db.queryForObject("select database()",String.class),
        "targetArtifactSha256",actualArtifactHash(),"authorizationFingerprint",authorizationFingerprint(input.domain().key()),
        "cutoverAuthorized",false);
  }

  @Transactional
  public Map<String,Object> execute(Approval approval,String approvalHash,byte[] restoreReceipt,byte[] comparisonInputs) {
    validate(approval,approvalHash);
    var input=migration.readFrozenInput(approval.snapshotId());
    String domain=input.domain().key();
    var states=db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update",domain);
    if(states.size()!=1 || !(states.getFirst().get("revision") instanceof Number revision) || revision.longValue()<0)
      throw conflict("Cutover cannot initialize or repair a missing/corrupt domain");
    var prior=db.queryForList("select * from summer_authorization_cutover where operation_id=? for update",approval.operationId());
    if(!prior.isEmpty()) {
      var receipt=prior.getFirst();
      if(!domain.equals(receipt.get("domain_key")) || !approvalHash.equals(receipt.get("approval_sha256")))
        throw conflict("Cutover operation identity conflict");
      if(!"MANAGED".equals(states.getFirst().get("engine_mode"))
          || revision.longValue()<((Number)receipt.get("committed_revision")).longValue()
          || db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?",Integer.class,domain)!=1)
        throw conflict("Completed cutover requires verified recovery; replay cannot repair it");
      // Revocations and subsequent legitimate grants must survive an exact replay.
      return report(receipt);
    }
    if(!"LEGACY".equals(states.getFirst().get("engine_mode"))
        || revision.longValue()!=approval.expectedRevision() || input.expectedRevision()!=approval.expectedRevision())
      throw conflict("Exact existing LEGACY checkpoint required");
    if(!input.domain().kind().equals("ENTERPRISE") || !input.sourceFingerprint().equals(approval.sourceFingerprint())
        || !approval.targetSchema().equals(db.queryForObject("select database()",String.class))
        || !actualArtifactHash().equals(approval.targetArtifactSha256()))
      throw conflict("Cutover domain/schema/executing artifact binding mismatch");
    var fenced=fence.requireSnapshot(approval.snapshotId());
    if(!json.readTree(input.evidenceJson()).path("legacyFactsFingerprint").asString()
        .equals(legacy.currentLegacyFactsFingerprint(input.domain())))
      throw conflict("Legacy source changed after capture");
    runtime.requireNoTargetRuntimeAssociation(input);
    if(input.plan().blockers().stream().anyMatch(blocker->!blocker.startsWith("PENDING_DATA:")))
      throw conflict("Unresolved identity/runtime blockers forbid cutover");
    for(String table:List.of("summer_authorization_migration_step","summer_authorization_data_migration_step"))
      if(db.queryForObject("select count(*) from "+table+" where snapshot_id=? and applied=false",Integer.class,approval.snapshotId())!=0)
        throw conflict("All frozen migration steps must complete");
    if(!authorizationFingerprint(domain).equals(approval.authorizationFingerprint()))
      throw conflict("Staged authorization facts changed since approval/comparison");
    validateRestore(approval,restoreReceipt,domain);
    var proof=data.report(approval.snapshotId());
    validateStaged(input,data.requireApplied(approval.snapshotId()));
    boolean comparison=approval.purpose().equals("ISOLATED_COMPARISON");
    if(!approval.planHash().equals(proof.get("planHash")) || !data.dryRun(approval.snapshotId()).blockers().isEmpty()
        || !(comparison?Set.of("APPLIED","VERIFIED"):Set.of("VERIFIED")).contains(proof.get("status")))
      throw conflict("Complete current DATA proof required for this operation");
    if(!comparison) {
      var encoded=db.queryForObject("select verification_json from summer_authorization_data_migration where snapshot_id=?",String.class,approval.snapshotId());
      if(encoded==null || !LegacyAuthorizationReader.digest(encoded).equals(approval.verificationSha256())
          || !Boolean.TRUE.equals(proof.get("dataConversionAccepted")))
        throw conflict("Exact source-bound DATA verification receipt required");
      validateComparison(approval,comparisonInputs,json.readTree(encoded).path("runtimeInputsFingerprint").asString(),fenced);
    }
    if(db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?",Integer.class,domain)!=0)
      throw conflict("Unexpected existing bootstrap marker; use verified recovery");
    if(db.update("update summer_authorization_domain set engine_mode='MANAGED' where domain_key=? and engine_mode='LEGACY' and revision=?",domain,approval.expectedRevision())!=1)
      throw conflict("Cutover lost its exact checkpoint");
    audit.mutation(domain,approval.snapshotId(),"AUTHORIZATION_"+approval.purpose(),approval.requestId(),approval.actor());
    events.enqueue(input.domain(),"MIGRATION",approval.snapshotId(),"AUTHORIZATION_"+approval.purpose(),1,approval.requestId());
    long committed=db.queryForObject("select revision from summer_authorization_domain where domain_key=?",Long.class,domain);
    if(committed<=approval.expectedRevision())throw conflict("Cutover revision did not advance");
    db.update("insert into summer_permission_bootstrap values (?,CURRENT_TIMESTAMP(6))",domain);
    db.update("insert into summer_authorization_cutover(operation_id,snapshot_id,domain_key,purpose,approval_sha256,source_fingerprint,authorization_fingerprint,target_artifact_sha256,from_revision,committed_revision,evidence_json,actor,request_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
        approval.operationId(),approval.snapshotId(),domain,approval.purpose(),approvalHash,input.sourceFingerprint(),
        approval.authorizationFingerprint(),approval.targetArtifactSha256(),approval.expectedRevision(),committed,
        json.writeValueAsString(Map.of("restoreReceiptSha256",approval.restoreReceiptSha256(),"planHash",approval.planHash(),
            "verificationSha256",Objects.toString(approval.verificationSha256(),""))),approval.actor(),approval.requestId());
    return report(db.queryForMap("select * from summer_authorization_cutover where operation_id=?",approval.operationId()));
  }

  private void validateStaged(AuthorizationMigrationService.DataInput input,
      com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan dataPlan) {
    String domain=input.domain().key();
    var expectedPermissions=new TreeSet<String>();
    input.plan().permissions().forEach(permission->expectedPermissions.add(permission.id()));
    dataPlan.grants().forEach(grant->expectedPermissions.add(grant.id()));
    if(!new ArrayList<>(expectedPermissions).equals(db.queryForList("select permission_id from summer_permission where domain_key=? order by permission_id",String.class,domain)))
      throw conflict("Unexpected or missing target permission facts");
    var expectedResources=new ArrayList<String>();
    for(var permission:input.plan().permissions()) {
      resolvers.stream().filter(resolver->resolver.type().equals(permission.type())).findFirst()
          .orElseThrow(()->conflict("Trusted target resource resolver required"))
          .resolve(input.domain(),permission.resourceId());
      var state=db.queryForList("select code,type,access_mode,status from summer_permission where domain_key=? and permission_id=?",domain,permission.id());
      if(state.size()!=1 || !state.getFirst().equals(Map.of("code",permission.id(),"type",permission.type(),"access_mode",permission.accessMode(),"status","ACTIVE")))
        throw conflict("Applied identity permission drift");
      expectedResources.add(permission.id()+":"+permission.type()+":"+permission.resourceId());
    }
    var resources=db.queryForList("select permission_id,resource_type,resource_id from summer_permission_resource where domain_key=?",domain).stream()
        .map(row->row.get("permission_id")+":"+row.get("resource_type")+":"+row.get("resource_id")).sorted().toList();
    if(!expectedResources.stream().sorted().toList().equals(resources))throw conflict("Applied resource associations drift");
    var expectedRoles=new TreeSet<String>();
    input.plan().roles().forEach(role->{
      expectedRoles.add(role.id());
      if(!role.permissionIds().stream().sorted().toList().equals(db.queryForList("select permission_id from summer_role_permission where domain_key=? and role_id=? order by permission_id",String.class,domain,role.id())))
        throw conflict("Applied identity role drift");
    });
    dataPlan.vectors().forEach(vector->expectedRoles.add(vector.targetRoleId()));
    if(!new ArrayList<>(expectedRoles).equals(db.queryForList("select role_id from summer_permission_role where domain_key=? order by role_id",String.class,domain)))
      throw conflict("Unexpected or missing target roles");
    if(!input.plan().assignments().stream().map(assignment->assignment.subject()).sorted().toList()
        .equals(db.queryForList("select username from summer_permission_user where domain_key=? order by username",String.class,domain)))
      throw conflict("Unexpected or missing target subjects");
  }

  private void validateComparison(Approval approval,byte[] bytes,String fingerprint,Map<String,Object> fenced) {
    if(bytes==null || !hash(bytes).equals(approval.comparisonInputsSha256()))
      throw conflict("Pinned actual HTTP comparison inputs required");
    var inputs=json.readTree(bytes);
    if(!LegacyAuthorizationReader.digest(json.writeValueAsString(sorted(json.convertValue(inputs,Object.class)))).equals(fingerprint))
      throw conflict("Comparison input fingerprint differs from the verified DATA receipt");
    var bindings=inputs.path("runtime").path("controlledUpgrade");
    var enrollment=db.queryForMap("select bridge_sha256 from summer_authorization_bridge_enrollment where domain_key=?",fenced.get("domain_key"));
    var expected=Map.of("sourceFingerprint",approval.sourceFingerprint(),"authorizationFingerprint",approval.authorizationFingerprint(),
        "targetArtifactSha256",approval.targetArtifactSha256(),"planHash",approval.planHash(),
        "restoreReceiptSha256",approval.restoreReceiptSha256(),"oldArtifactSha256",enrollment.get("bridge_sha256"),
        "sourceSchema",fenced.get("source_schema"),"sourceExecutionReceiptSha256",fenced.get("execution_receipt_sha256"));
    for(var entry:expected.entrySet())
      if(!entry.getValue().equals(bindings.path(entry.getKey()).asString()))
        throw conflict("HTTP comparison does not bind the actual staged upgrade inputs");
  }

  private Object sorted(Object value) {
    if(value instanceof Map<?,?> map) {
      var result=new TreeMap<String,Object>();
      map.forEach((key,item)->result.put(key.toString(),sorted(item)));return result;
    }
    if(value instanceof List<?> list)return list.stream().map(this::sorted).toList();
    return value;
  }

  private void validateRestore(Approval approval,byte[] bytes,String domain) {
    if(bytes==null || !hash(bytes).equals(approval.restoreReceiptSha256()))throw conflict("Pinned verified restore receipt required");
    var receipt=json.readTree(bytes);
    if(!"summer-verified-isolated-restore/1".equals(receipt.path("format").asString())
        || !receipt.path("verified").isBoolean() || !receipt.path("verified").asBoolean()
        || !domain.equals(receipt.path("domainKey").asString())
        || !approval.sourceFingerprint().equals(receipt.path("sourceFingerprint").asString())
        || receipt.path("sourceSchema").asString().equals(receipt.path("targetSchema").asString())
        || !receipt.path("backupManifestSha256").asString().matches("[0-9a-f]{64}"))
      throw conflict("Authenticated isolated restore/source binding required");
    // Comparison runs only against the restored copy. This recipe promotes the fenced source;
    // a comparison clone already has MANAGED state and is never a production promotion target.
    String expected=approval.purpose().equals("ISOLATED_COMPARISON")?receipt.path("targetSchema").asString():approval.targetSchema();
    if(!approval.targetSchema().equals(expected)
        || !Set.of(receipt.path("sourceSchema").asString(),receipt.path("targetSchema").asString()).contains(approval.targetSchema()))
      throw conflict("Approved target is outside the verified restore pair");
    if(approval.purpose().equals("PRODUCTION_CUTOVER")
        && !approval.targetSchema().equals(receipt.path("sourceSchema").asString()))
      throw conflict("This recipe promotes only the fenced source; comparison clones cannot be promoted");
  }

  private String authorizationFingerprint(String domain) {
    var facts=new TreeMap<String,Object>();
    for(String table:List.of("summer_permission_identity","summer_permission_group","summer_permission",
        "summer_permission_role","summer_role_permission", "summer_role_platform_permission","summer_permission_user","summer_permission_user_role",
        "summer_permission_resource","summer_permission_resource_control","summer_permission_data_scope","summer_permission_selected_department")) {
      var rows=db.queryForList("select * from "+table+" where domain_key=?",domain).stream()
          .map(row->json.writeValueAsString(new TreeMap<>(row))).sorted().toList();
      facts.put(table,rows);
    }
    var roles=db.queryForList("select r.role_id,r.status,r.version from summer_authorization_role r join summer_permission_role p on p.role_id=r.role_id where p.domain_key=?",domain);
    facts.put("roleStates",roles.stream().map(row->json.writeValueAsString(new TreeMap<>(row))).sorted().toList());
    var users=db.queryForList("select u.username,u.active from summer_auth_user u join summer_permission_user p on p.username=u.username where p.domain_key=?",domain);
    facts.put("userStates",users.stream().map(row->json.writeValueAsString(new TreeMap<>(row))).sorted().toList());
    if(domain.startsWith("ENTERPRISE:")) {
      var members=db.queryForList("select subject,status,version from summer_enterprise_member where enterprise_id=?",domain.substring(11));
      facts.put("memberStates",members.stream().map(row->json.writeValueAsString(new TreeMap<>(row))).sorted().toList());
    }
    return LegacyAuthorizationReader.digest(json.writeValueAsString(facts));
  }

  public String actualArtifactHash() {
    // Source bootRun is useful for development but cannot claim a release cutover artifact.
    try {
      var process=ProcessHandle.current().info().arguments().orElseThrow();
      var args=List.of(process);int index=args.indexOf("-jar");
      if(index<0 || index+2!=args.size())throw new IllegalArgumentException();
      Path file=Path.of(args.get(index+1)).toRealPath();
      if(!Files.isRegularFile(file))throw new IllegalArgumentException();
      var digest=java.security.MessageDigest.getInstance("SHA-256");
      try(var stream=Files.newInputStream(file)) {
        byte[] buffer=new byte[65536];int count;
        while((count=stream.read(buffer))!=-1)digest.update(buffer,0,count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch(Exception unavailable) {throw conflict("An actual executing release JAR is required for cutover", unavailable);}
  }

  private Map<String,Object> report(Map<String,Object> receipt) {
    return Map.of("operationId",receipt.get("operation_id"),"domainKey",receipt.get("domain_key"),
        "purpose",receipt.get("purpose"),"committedRevision",receipt.get("committed_revision"),
        "cutoverAuthorized",receipt.get("purpose").equals("PRODUCTION_CUTOVER"));
  }

  static void validate(Approval a,String approvalHash) {
    if(a==null || !"summer-controlled-cutover/1".equals(a.format())
        || !Set.of("ISOLATED_COMPARISON","PRODUCTION_CUTOVER").contains(Objects.toString(a.purpose(),""))
        || a.actor()==null || a.actor().isBlank() || a.actor().length()>128
        || a.operationId()==null || !a.operationId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.requestId()==null || !a.requestId().matches("[A-Za-z0-9._:-]{1,128}")
        || a.expectedRevision()<0 || a.targetSchema()==null || !a.targetSchema().matches("[A-Za-z0-9_]{1,64}"))
      throw conflict("Complete explicit cutover approval required");
    for(String value:List.of(Objects.toString(approvalHash,""),Objects.toString(a.sourceFingerprint(),""),
        Objects.toString(a.targetArtifactSha256(),""),Objects.toString(a.authorizationFingerprint(),""),
        Objects.toString(a.restoreReceiptSha256(),""),Objects.toString(a.planHash(),"")))
      if(!value.matches("[0-9a-f]{64}"))throw conflict("Exact cutover fingerprints required");
    if(a.purpose().equals("PRODUCTION_CUTOVER") && (a.verificationSha256()==null || !a.verificationSha256().matches("[0-9a-f]{64}")
        || a.comparisonInputsSha256()==null || !a.comparisonInputsSha256().matches("[0-9a-f]{64}")))
      throw conflict("Production cutover requires exact verification fingerprint");
  }

  private static String hash(byte[] bytes) {
    try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  private static SummerException conflict(String message) {return SummerException.withMessage(PermissionErrorCode.CONFLICT,message);}
  private static SummerException conflict(String message, Throwable cause) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message, cause);
  }

}
