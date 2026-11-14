package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyBusinessDelegationHttpTest {
  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources resources;
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  org.springframework.data.redis.core.StringRedisTemplate redis;

  @org.junit.jupiter.api.BeforeEach void redisUnavailableWithoutConnectionSetup() {
    resources.scan();
    String domain = "ENTERPRISE:ent-acme";
    for (String[] route : List.of(new String[]{"POST", "/api/system-parameters", "management-write"},
        new String[]{"GET", "/api/system-parameters/{key}", "management-read"})) {
      String permission = db.queryForObject("select permission_id from summer_permission where domain_key=? and code=?", String.class, domain, route[2]);
      String resource = db.queryForObject("select resource_id from summer_interface_resource where http_method=? and normalized_path=? and active=true", String.class, route[0], route[1]);
      db.update("insert into summer_permission_resource values (?,?,'INTERFACE',?)", domain, permission, resource);
    }
    String group = db.queryForObject("select group_id from summer_permission_group where domain_key=? order by group_id limit 1", String.class, domain);
    for (String action : List.of("read", "create")) {
      String id = "delegation-" + action;
      db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values (?,?,?,'DATA')", domain, id, id);
      db.update("insert into summer_permission(domain_key,permission_id,code,name,description,type,access_mode,group_id,status,version) values (?,?,?,?,null,'DATA','PUBLIC',?,'ACTIVE',1)", domain, id, id, id, group);
      db.update("insert into summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset) values (?,?,'system-parameters',?,'TENANT_ALL')", domain, id, action);
    }
    // This HTTP delegation contract exercises the real database, independently of Redis startup latency.
    org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis unavailable in HTTP contract fixture"))
        .when(redis).opsForValue();
  }
  @Test void convertedLegacyBusinessAuthorityCannotReplaceRawOAuthWriteDelegation() throws Exception {
    var actor=jwt().jwt(j -> j.subject("summer-user").claim("scope","system.parameter.read")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
        .claim("enterprise_capabilities",Map.of("ent-acme",List.of("system.parameter.write"))))
        .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    mvc.perform(post("/api/system-parameters").with(actor).with(csrf()).contentType("application/json")
        .content("{\"key\":\"raw10.denied\",\"type\":\"STRING\",\"value\":\"denied\"}"))
        .andExpect(status().isForbidden());
  }
  @Test void managedReadAndWriteAliasesRemainSeparateRawDelegationBounds() throws Exception {
    mvc.perform(post("/api/system-parameters").with(actor("platform.parameter.write", "system.parameter.write"))
        .with(csrf()).contentType("application/json")
        .content("{\"key\":\"raw10.alias\",\"type\":\"STRING\",\"value\":\"alias\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/system-parameters/raw10.alias").with(actor("system.parameter.write", "system.parameter.read")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/system-parameters/raw10.alias").with(actor("platform.parameter.read", "system.parameter.read")))
        .andExpect(status().isOk());
    mvc.perform(post("/api/system-parameters").with(actor("platform.parameter.read", "system.parameter.write"))
        .with(csrf()).contentType("application/json")
        .content("{\"key\":\"raw10.alias-denied\",\"type\":\"STRING\",\"value\":\"denied\"}"))
        .andExpect(status().isForbidden());
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String raw,String authority) {
    return jwt().jwt(j -> j.subject("summer-user").claim("scope",raw)
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
        .claim("enterprise_capabilities",Map.of("ent-acme",List.of("system.parameter.read","system.parameter.write"))))
        .authorities(new SimpleGrantedAuthority("SCOPE_"+authority));
  }
}
