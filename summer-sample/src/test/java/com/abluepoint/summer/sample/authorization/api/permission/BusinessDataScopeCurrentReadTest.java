package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;

import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** H2 constraint seam; the same stale-RR case is also required against native MySQL. */
@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessDataScopeCurrentReadTest {
  @Autowired BusinessDataScopes scopes;
  @Autowired JdbcTemplate db;
  @Autowired PlatformTransactionManager transactions;

  @Test void oldRepeatableReadCannotRetainAllowForCurrentLegacyOrMissingMode() {
    grant("read");
    assertEquals(1, db.queryForObject("select count(*) from summer_permission where permission_id='current-read'", Integer.class));
    assertEquals(1, db.queryForObject("select count(*) from summer_permission_data_scope where permission_id='current-read'", Integer.class));
    db.update("insert into summer_system_parameter(id,tenant_id,owner_id,created_by,parameter_key,value_type,value_json,required_flag,public_flag,status,version,created_at,updated_at) values('scope10-current','ent-acme','summer-user','summer-user','scope10.current','STRING','\"safe\"',false,false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("scope10-current-fixture")
        .header("alg", "none").subject("summer-user")
        .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300))
        .claim("enterprise_memberships", List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
    var actor = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token, List.of());
    var query = new NamedParameterJdbcTemplate(db);
    java.util.function.Function<BusinessDataScopes.Filter, Long> count = range -> query.queryForObject(
        "select count(*) from summer_system_parameter where parameter_key='scope10.current' and ("
            + range.sqlPredicate() + ")", range.sqlBindings(), Long.class);
    assertEquals(1L, db.queryForObject("select count(*) from summer_system_parameter where parameter_key='scope10.current'", Long.class));
    Long warmed = scopes.read("ent-acme", "system-parameters", "read", actor, count);
    assertEquals(1L, warmed.longValue());
    var outer = new TransactionTemplate(transactions);
    outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    var fresh = new TransactionTemplate(transactions);
    fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    outer.executeWithoutResult(tx -> {
      assertEquals("MANAGED", db.queryForObject("select engine_mode from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'", String.class));
      fresh.executeWithoutResult(current -> db.update("update summer_authorization_domain set engine_mode='LEGACY',revision=1 where domain_key='ENTERPRISE:ent-acme'"));
      assertEquals("MANAGED", db.queryForObject("select engine_mode from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'", String.class));
      assertThrows(AccessDeniedException.class, () -> scopes.read("ent-acme", "system-parameters", "read", actor, count));
    });
    db.update("update summer_authorization_domain set engine_mode='MANAGED',revision=2 where domain_key='ENTERPRISE:ent-acme'");
    db.update("delete from summer_permission_data_scope where permission_id='current-read'");
    assertEquals(0L, scopes.read("ent-acme", "system-parameters", "read", actor, count));
    db.update("delete from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'");
    var called = new AtomicBoolean();
    assertThrows(AccessDeniedException.class, () -> scopes.read("ent-acme", "system-parameters", "read", actor, range -> {
      called.set(true); return count.apply(range);
    }));
    assertFalse(called.get());
    assertEquals(0, db.queryForObject("select count(*) from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'", Integer.class));
  }
  @Test void invalidRevisionNeverSelectsLegacyAndWriteFilterCannotEscapeItsTransaction() {
    grant("create");
    var token=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("scope10-write-lease")
        .header("alg","none").subject("summer-user").issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300))
        .claim("enterprise_memberships",List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))).build();
    var actor=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,List.of());
    var escaped=new java.util.concurrent.atomic.AtomicReference<BusinessDataScopes.Filter>();
    var other=new TransactionTemplate(transactions);
    other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    scopes.write("ent-acme","system-parameters",java.util.Set.of("create"),actor,ranges -> {
      escaped.set(ranges.get("create"));
      other.executeWithoutResult(tx -> assertThrows(AccessDeniedException.class, () ->
          scopes.write("ent-acme","system-parameters",java.util.Set.of("create"),actor,ignored -> null)));
      return null;
    });
    assertThrows(AccessDeniedException.class, () -> scopes.insert(escaped.get(),BusinessDataScopes.Table.PARAMETERS,java.util.Map.of()));
    db.update("update summer_authorization_domain set revision=-1 where domain_key='ENTERPRISE:ent-acme'");
    var invoked=new AtomicBoolean();
    assertThrows(AccessDeniedException.class, () -> scopes.read("ent-acme","system-parameters","read",actor,range -> {invoked.set(true);return 1;}));
    assertThrows(AccessDeniedException.class, () -> scopes.write("ent-acme","system-parameters",java.util.Set.of("create"),actor,ranges -> {invoked.set(true);return 1;}));
    assertFalse(invoked.get());
  }

  private void grant(String action) {
    String domain = "ENTERPRISE:ent-acme", id = "current-" + action;
    String group = db.queryForObject("select group_id from summer_permission_group where domain_key=? order by group_id limit 1", String.class, domain);
    db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values (?,?,?,'DATA')", domain, id, id);
    db.update("insert into summer_permission(domain_key,permission_id,code,name,description,type,access_mode,group_id,status,version) values (?,?,?,?,null,'DATA','PUBLIC',?,'ACTIVE',1)", domain, id, id, id, group);
    db.update("insert into summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset) values (?,?,'system-parameters',?,'TENANT_ALL')", domain, id, action);
  }

}
