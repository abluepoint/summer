package com.abluepoint.summer.sample.dictionary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="summer.sample.parameter-events.scheduling-enabled=false")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConfigurationRequestIdCapacityHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", false, "operator");
        managed.configuration("ent-acme", "system-parameters", false, "operator");
        managed.interfaces("PLATFORM:platform", "operator", java.util.Set.of("customers"));
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() { managed.close(); }

  private static final String ID="r".repeat(128);
  @Autowired MockMvc http;
  @Autowired JdbcTemplate db;
  private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
    return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(j -> j.subject("operator").claim("scope", "system.parameter.write platform.dictionary.write customer.write").claim("enterprise_memberships",java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
        .authorities(java.util.List.of("system.parameter.write","platform.dictionary.write","customer.write").stream()
            .<org.springframework.security.core.GrantedAuthority>map(s -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_"+s)).toList());
  }
  private static void failureShape(org.springframework.test.web.servlet.MvcResult result) {
    var tables=new java.util.TreeSet<String>();var types=new java.util.ArrayList<String>();boolean narrow=false;
    for(Throwable failure=result.getResolvedException();failure!=null;failure=failure.getCause()) {
      types.add(failure.getClass().getSimpleName());String message=String.valueOf(failure.getMessage());
      var matcher=java.util.regex.Pattern.compile("(?i)(?:insert into|update) (summer_[a-z_]+)").matcher(message);
      while(matcher.find()) tables.add(matcher.group(1));
      narrow|=message.contains("CHARACTER VARYING(120)");
    }
    if(result.getResponse().getStatus()>=400) System.out.println("capacity_failure tables="+tables+" types="+types+" varchar120="+narrow);
  }
  private void parameter(String key,String requestId) throws Exception {
    http.perform(post("/api/system-parameters").with(writer()).header("X-Request-Id",requestId).contentType("application/json")
        .content("{\"key\":\""+key+"\",\"type\":\"INTEGER\",\"value\":25}"))
        .andDo(ConfigurationRequestIdCapacityHttpTest::failureShape).andExpect(status().isCreated()).andExpect(header().string("X-Request-Id",requestId));
  }
  @Test void parameterOutboxAndAuditPreserveTheAccepted128Characters() throws Exception {
    parameter("capacity.parameter",ID);
    assertEquals(128,db.queryForObject("select length(request_id) from summer_system_parameter_outbox where parameter_key='capacity.parameter'",Integer.class));
    assertEquals(128,db.queryForObject("select length(request_id) from summer_audit_event where resource='system-parameter:capacity.parameter'",Integer.class));
  }
  @Test void dictionaryVersionOutboxAndNotificationPreserveTheAccepted128Characters() throws Exception {
    String base="/api/dictionaries/capacity-dictionary";
    http.perform(post("/api/dictionaries").with(writer()).contentType("application/json").content("{\"code\":\"capacity-dictionary\",\"name\":\"Capacity\",\"publicReadable\":true,\"enabled\":true}"))
        .andExpect(status().isOk());
    http.perform(post(base+"/publish").with(writer()).header("X-Request-Id",ID).contentType("application/json")
        .content(DraftRevisionFixture.lifecycle(http,base+"/publish","{\"expectedVersion\":0}")))
        .andDo(ConfigurationRequestIdCapacityHttpTest::failureShape).andExpect(status().isOk()).andExpect(header().string("X-Request-Id",ID));
    for(String table:java.util.List.of("summer_dictionary_version","summer_dictionary_outbox","summer_dictionary_notification_event"))
      assertEquals(128,db.queryForObject("select length(request_id) from "+table+" where dictionary_code='capacity-dictionary'",Integer.class));
  }
  @Test void overrideRevisionPreservesTheAccepted128Characters() throws Exception {
    parameter("capacity.override","short-setup");
    http.perform(put("/api/system-parameters/capacity.override/overrides/org-engineering").with(writer()).header("X-Request-Id",ID)
        .contentType("application/json").content("{\"value\":30}"))
        .andDo(ConfigurationRequestIdCapacityHttpTest::failureShape).andExpect(status().isOk()).andExpect(header().string("X-Request-Id",ID));
    assertEquals(128,db.queryForObject("select length(request_id) from summer_configuration_override_revision",Integer.class));
  }
  @Test void customerAuditPreservesTheAccepted128Characters() throws Exception {
    http.perform(post("/api/customers").with(writer()).header("X-Request-Id",ID).contentType("application/json")
        .content("{\"username\":\"capacity-customer\",\"email\":\"capacity@example.test\",\"displayName\":\"Capacity\"}"))
        .andDo(ConfigurationRequestIdCapacityHttpTest::failureShape).andExpect(status().isOk()).andExpect(header().string("X-Request-Id",ID));
    assertEquals(128,db.queryForObject("select length(request_id) from summer_audit_event where resource='customer:capacity-customer'",Integer.class));
  }
}
