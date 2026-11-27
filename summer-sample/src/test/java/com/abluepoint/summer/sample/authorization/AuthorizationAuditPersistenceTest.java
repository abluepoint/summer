package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.context.jdbc.Sql("/fixtures/menu-client-dictionary.sql")
@SpringBootTest(properties="summer.security.storage.keys=memory")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationAuditPersistenceTest {
  static final Domain ENTERPRISE=Domain.enterprise("ent-acme"), PLATFORM=Domain.platform();
  @Autowired ManagedRoleService roles;
  @Autowired AuthorizationService retired;
  @Autowired UnifiedAuthorizationService unified;
  @Autowired AuthorizationChangeEventOutboxRepository outbox;
  @Autowired ApplicationContext context;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CurrentManagedAuthorizationTestFixture fixture;
  @Autowired ManagedInterfaceResources resources;
  @Autowired org.springframework.beans.factory.ObjectProvider<RequestMappingHandlerMapping> mappings;

  @BeforeEach void initializeUnpublishedMenuState() {
    fixture.grantHandlers(PLATFORM,"audit-admin",(h,p,m)->(p.equals("/api/v1/platform/menu-manifest") || p.equals("/api/v1/platform/authorization/menu-layout")));
    fixture.grantHandlers(ENTERPRISE,"audit-admin",(h,p,m)->p.startsWith("/api/v1/enterprises/{enterpriseId}/menu-manifest"));
    jdbc.update("update summer_menu_publication set published=false,manifest_version=0 where publication_id='global'");
  }
  private AuthorizationAuditStore freshAuditStore() { return context.getAutowireCapableBeanFactory().createBean(AuthorizationAuditStore.class); }
  private Map<String,Object> create(String code,String request) {
    return (Map<String,Object>) roles.mutate(ENTERPRISE,"audit-admin","role",null,"POST",Map.of("name","Reader","code",code,"permissionIds",List.of()),null,code,request).body();
  }
  private JwtAuthenticationToken principal(String name) {
    return new JwtAuthenticationToken(Jwt.withTokenValue("test-only").header("alg","none").subject(name)
        .claim("scope","enterprise.menu.write platform.menu.write platform.capabilities.write enterprise.roles.read enterprise.roles.write customer.read").build(),
        java.util.Arrays.stream(new String[]{"enterprise.menu.write", "platform.menu.write", "platform.capabilities.write", "enterprise.roles.read", "enterprise.roles.write", "customer.read"})
            .map(scope -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope)).toList());
  }
  private ResourceAuthorization.Resource resource(String method,String path) {
    for(var mapping:mappings.orderedStream().toList()) for(var entry:mapping.getHandlerMethods().entrySet())
      if(entry.getKey().getPatternValues().contains(path) && entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(m->m.name().equals(method)))
        return resources.registered(ManagedInterfaceResources.mvc(method,path,entry.getValue(),entry.getKey()));
    throw new IllegalArgumentException("Exact installed test mapping missing");
  }
  @Test void committedMutationAuditCanBeReadByAnIndependentServiceInstance() {
    String id=create("audit-reader","audit-request").get("roleId").toString();
    assertThat(freshAuditStore().read(ENTERPRISE,id)).singleElement().satisfies(row->assertThat(row)
        .containsEntry("action","ROLE_role_POST").containsEntry("requestId","audit-request").containsEntry("principal","audit-admin").containsKey("at"));
    assertThat(outbox.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(ENTERPRISE.key(),"ROLE")).anyMatch(e->e.resourceId().equals(id));
  }
  @Test void rolledBackMutationLeavesNeitherAuditNorOutboxEvent() {
    var id=new AtomicReference<String>(); long before=outbox.count();
    new TransactionTemplate(transactions).executeWithoutResult(tx->{
      id.set(create("audit-rollback","audit-rollback-request").get("roleId").toString());
      assertThat(freshAuditStore().read(ENTERPRISE,id.get())).hasSize(1);
      assertThat(outbox.count()).isGreaterThan(before); tx.setRollbackOnly();
    });
    assertThat(freshAuditStore().read(ENTERPRISE,id.get())).isEmpty(); assertThat(outbox.count()).isEqualTo(before);
    assertThat(jdbc.queryForObject("select count(*) from summer_permission_mutation where domain_key=? and idempotency_key=?",Long.class,ENTERPRISE.key(),"audit-rollback")).isZero();
  }
  @Test void authorizationDecisionsRemainDurableWhenAnOuterTransactionRollsBack() {
    fixture.grantHandlers(PLATFORM,"alice",(h,p,m)->p.equals("/api/customers") && m.equals("GET"));
    var read=resource("GET","/api/customers"); var write=resource("POST","/api/customers");
    new TransactionTemplate(transactions).executeWithoutResult(tx->{
      assertThat(unified.decide(new ResourceAuthorization.Request(PLATFORM,principal("alice"),read,"allow-request")).allowed()).isTrue();
      assertThat(unified.decide(new ResourceAuthorization.Request(PLATFORM,principal("alice"),write,"deny-request")).allowed()).isFalse(); tx.setRollbackOnly();
    });
    var rows=freshAuditStore().read(PLATFORM,"alice");
    assertThat(rows).anySatisfy(row->assertThat(row).containsEntry("action","RESOURCE_AUTHORIZATION_ALLOWED").containsEntry("requestId","allow-request")
        .containsEntry("principal","alice").containsEntry("resourceId",read.id()).containsEntry("decision","ALLOW").containsEntry("reasonCode","CURRENT_PERMISSION_ALLOW").containsKey("at"));
    assertThat(rows).anySatisfy(row->assertThat(row).containsEntry("action","RESOURCE_AUTHORIZATION_DENIED").containsEntry("requestId","deny-request")
        .containsEntry("resourceId",write.id()).containsEntry("decision","DENY").containsEntry("reasonCode","NO_APPLICABLE_PERMISSION"));
  }
  @Test void menuSyncAndOverrideHaveDurableAuditsAlongsideTheirOutboxEvents() throws Exception {
    syncMenu();
    mvc.perform(patch("/api/v1/platform/authorization/menu-layout").param("menuId","audit-menu").with(authentication(principal("audit-admin")))
        .contentType("application/json").header("Idempotency-Key","audit-menu-override").header("If-Match","\"1\"")
        .header("X-Request-Id","menu-override-request").content("{\"enabled\":false}")).andExpect(status().isOk());
    assertThat(freshAuditStore().read(PLATFORM,"manifest")).anySatisfy(row->assertThat(row).containsEntry("action","MENU_MANIFEST_PUBLISHED").containsEntry("requestId","menu-sync-request").containsEntry("principal","audit-admin"));
    // Current metadata writes remain in their complete domain; historical bare rows are never unioned.
    assertThat(freshAuditStore().read(PLATFORM,"audit-menu")).anySatisfy(row->assertThat(row).containsEntry("action","MENU_LAYOUT_CHANGED").containsEntry("requestId","menu-override-request"));
    assertThat(freshAuditStore().read(ENTERPRISE,"manifest")).isEmpty();
    assertThat(freshAuditStore().read(ENTERPRISE.id(),"audit-menu")).isEmpty();
    assertThat(outbox.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(PLATFORM.key(),"MENU")).filteredOn(e->e.resourceId().equals("manifest")).singleElement().satisfies(e->{ assertThat(e.enterpriseId()).isEqualTo(PLATFORM.key()); assertThat(e.resourceId()).isEqualTo("manifest"); assertThat(e.eventType()).isEqualTo("MENU_CHANGED"); assertThat(e.requestId()).isEqualTo("menu-sync-request"); });
    assertThat(outbox.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(PLATFORM.key(),"MENU")).filteredOn(e->e.resourceId().equals("audit-menu")).singleElement().satisfies(e->{ assertThat(e.resourceId()).isEqualTo("audit-menu"); assertThat(e.eventType()).isEqualTo("MENU_CHANGED"); assertThat(e.requestId()).isEqualTo("menu-override-request"); });
  }
  @Test void menuMutationAuditAndOutboxRollBackTogether() {
    long before=outbox.count();
    new TransactionTemplate(transactions).executeWithoutResult(tx->{
      try { syncMenu(); } catch(Exception e) { throw new IllegalStateException(e); }
      assertThat(freshAuditStore().read(PLATFORM,"manifest")).hasSize(1);
      assertThat(jdbc.queryForObject("select manifest_version from summer_menu_publication where publication_id='global'",Long.class)).isEqualTo(1L);
      assertThat(outbox.count()).isGreaterThan(before); tx.setRollbackOnly();
    });
    assertThat(freshAuditStore().read(PLATFORM,"manifest")).isEmpty();
    assertThat(jdbc.queryForObject("select manifest_version from summer_menu_publication where publication_id='global'",Long.class)).isZero();
    assertThat(outbox.count()).isEqualTo(before);
  }
  @Test void retiredEarlyBranchesCannotGrantWhileCurrentDecisionReasonsArePersisted() {
    var read=resource("GET","/api/customers");
    fixture.activeUser(PLATFORM,"unassigned"); fixture.activeUser(PLATFORM,"inactive");
    jdbc.update("update summer_auth_user set active=false where username='inactive'");
    for(String subject:List.of("legacy","managed","summer-user","inactive"))
      assertThat(retired.platformDecision(principal(subject),"GET:/api/customers","retired-"+subject)).isEqualTo(new AuthorizationService.Decision(false,"CONTROLLED_MANAGED_UPGRADE_REQUIRED"));
    for(String subject:List.of("unassigned","inactive")) {
      assertThat(unified.decide(new ResourceAuthorization.Request(PLATFORM,principal(subject),read,subject+"-request")).allowed()).isFalse();
      assertThat(freshAuditStore().read(PLATFORM,subject)).anySatisfy(row->assertThat(row).containsEntry("decision","DENY").containsEntry("reasonCode","NO_APPLICABLE_PERMISSION").containsEntry("resourceId",read.id()));
    }
  }
  private void syncMenu() throws Exception {
    mvc.perform(put("/api/v1/platform/menu-manifest").with(authentication(principal("audit-admin"))).contentType("application/json")
        .header("If-Match","\"0\"").header("Idempotency-Key","audit-menu-sync").header("X-Request-Id","menu-sync-request")
        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"audit-menu\",\"path\":\"/audit-menu\",\"titleKey\":\"menu.audit\",\"iconKey\":\"icon.audit\",\"sortOrder\":1}]}")).andExpect(status().isOk());
  }
}
