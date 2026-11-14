package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Independent current-model service instances share transactions, receipts and the domain lock. */
@SpringBootTest
@Import(CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext
class AuthorizationVersionConcurrencyTest {
  static final Domain DOMAIN = Domain.enterprise("ent-acme");
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AuthorizationAuditStore audit;
  @Autowired AuthorizationChangeEventService events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CurrentManagedAuthorizationTestFixture fixture;

  private ManagedRoleService node() { return new ManagedRoleService(db,json,audit,events); }
  private Map<String,Object> create(String code) {
    return new TransactionTemplate(transactions).execute(s -> (Map<String,Object>) node().mutate(DOMAIN,"version-admin","role",null,"POST",
        Map.of("name","Original","code",code,"permissionIds",List.of()),null,code,"created").body());
  }
  private long receipts(String id) {
    return db.queryForObject("select count(*) from summer_permission_mutation where domain_key=? and operation_key like ?",Long.class,DOMAIN.key(),"%:"+id);
  }
  private List<Map<String,Object>> updates(String id) {
    return audit.read(DOMAIN,id).stream().filter(r -> "ROLE_role_PATCH".equals(r.get("action"))).toList();
  }
  private List<AuthorizationChangeEventView> changes(String id) {
    return events.list(DOMAIN,"ROLE").items().stream().filter(e -> e.resourceId().equals(id) && e.version()==2).toList();
  }

  @Test void twoNodesUpdatingTheSameVersionHaveOneWinnerAndOneMutationRecord() throws Exception {
    String id=create("race-"+UUID.randomUUID()).get("roleId").toString();
    var left=node(); var right=node(); var barrier=new CyclicBarrier(2);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(() -> update(left,"role",id,Map.of("name","writer-a"),"writer-a",barrier));
      var b=pool.submit(() -> update(right,"role",id,Map.of("name","writer-b"),"writer-b",barrier));
      var results=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
      assertThat(results.stream().filter("STALE"::equals)).hasSize(1);
      String winner=results.stream().filter(x -> !x.equals("STALE")).findFirst().orElseThrow();
      assertThat(node().role(DOMAIN,id)).containsEntry("name",winner).containsEntry("version",2L);
      assertThat(left.role(DOMAIN,id)).isEqualTo(right.role(DOMAIN,id));
      assertThat(updates(id)).extracting(r -> r.get("requestId")).containsExactly(winner);
      assertThat(changes(id)).extracting(AuthorizationChangeEventView::requestId).containsExactly(winner);
      assertThat(receipts(id)).isEqualTo(1);
    }
  }

  @Test void rolledBackRoleChangePublishesNeitherProjectionReceiptAuditNorOutbox() {
    var original=create("rollback-"+UUID.randomUUID()); String id=original.get("roleId").toString();
    var tx=new TransactionTemplate(transactions);
    tx.executeWithoutResult(s -> { node().mutate(DOMAIN,"version-admin","role",id,"PATCH",Map.of("name","Changed"),"\"1\"","retry","rolled-back"); s.setRollbackOnly(); });
    assertThat(node().role(DOMAIN,id)).isEqualTo(original);
    assertThat(updates(id)).isEmpty(); assertThat(changes(id)).isEmpty(); assertThat(receipts(id)).isZero();
    var retried=tx.execute(s -> node().mutate(DOMAIN,"version-admin","role",id,"PATCH",Map.of("name","Changed"),"\"1\"","retry","committed-retry").body());
    assertThat(node().role(DOMAIN,id)).isEqualTo(retried);
    assertThat(updates(id)).extracting(r -> r.get("requestId")).containsExactly("committed-retry");
    assertThat(changes(id)).extracting(AuthorizationChangeEventView::requestId).containsExactly("committed-retry");
    assertThat(receipts(id)).isEqualTo(1);
  }

  @Test void twoNodesChangingTheSameUserBindingHaveOneWinner() throws Exception {
    String subject="binding-"+UUID.randomUUID(); fixture.activeUser(DOMAIN,subject);
    var tx=new TransactionTemplate(transactions);
    tx.execute(s -> node().mutate(DOMAIN,"version-admin","user",subject,"PUT",Map.of("roleIds",List.of()),"\"0\"","binding-create","binding-created"));
    String roleA=create("binding-a-"+UUID.randomUUID()).get("roleId").toString();
    String roleB=create("binding-b-"+UUID.randomUUID()).get("roleId").toString();
    var barrier=new CyclicBarrier(2);
    try(var pool=Executors.newFixedThreadPool(2)) {
      var a=pool.submit(() -> update(node(),"user",subject,Map.of("roleIds",List.of(roleA)),"binding-a",barrier));
      var b=pool.submit(() -> update(node(),"user",subject,Map.of("roleIds",List.of(roleB)),"binding-b",barrier));
      var results=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
      assertThat(results.stream().filter("STALE"::equals)).hasSize(1);
      String winner=results.stream().filter(x -> !x.equals("STALE")).findFirst().orElseThrow();
      assertThat(node().user(DOMAIN,subject)).containsEntry("version",2L).containsEntry("roleIds",List.of(winner.equals("binding-a")?roleA:roleB));
      assertThat(audit.read(DOMAIN,subject)).extracting(r -> r.get("requestId")).containsExactly("binding-created",winner);
      assertThat(changes(subject)).extracting(AuthorizationChangeEventView::requestId).containsExactly(winner);
      assertThat(receipts(subject)).isEqualTo(2);
    }
  }

  private String update(ManagedRoleService service,String target,String id,Map<String,Object> body,String name,CyclicBarrier barrier) throws Exception {
    barrier.await(10,TimeUnit.SECONDS);
    try {
      new TransactionTemplate(transactions).execute(s -> service.mutate(DOMAIN,"version-admin",target,id,target.equals("user")?"PUT":"PATCH",body,"\"1\"",name,name));
      return name;
    } catch(SummerException stale) {
      if(stale.errorCode()!=SampleTransportErrorCode.PERMISSION_STALE) throw stale;
      return "STALE";
    }
  }
}
