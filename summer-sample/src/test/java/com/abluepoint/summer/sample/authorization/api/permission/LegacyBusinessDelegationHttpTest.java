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

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.ExplicitLegacyBusinessDomains.AcmeAndPlatform.class)
@org.springframework.test.annotation.DirtiesContext
class LegacyBusinessDelegationHttpTest {
  @Autowired MockMvc mvc;
  @org.springframework.test.context.bean.override.mockito.MockitoBean
  org.springframework.data.redis.core.StringRedisTemplate redis;

  @org.junit.jupiter.api.BeforeEach void redisUnavailableWithoutConnectionSetup() {
    // This HTTP delegation contract exercises the real database, independently of Redis startup latency.
    org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis unavailable in HTTP contract fixture"))
        .when(redis).opsForValue();
  }
  @Test void convertedLegacyBusinessAuthorityCannotReplaceRawOAuthWriteDelegation() throws Exception {
    var actor=jwt().jwt(j -> j.subject("operator").claim("scope","system.parameter.read")
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
        .claim("enterprise_capabilities",Map.of("ent-acme",List.of("system.parameter.write"))))
        .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"));
    mvc.perform(post("/api/system-parameters").with(actor).with(csrf()).contentType("application/json")
        .content("{\"key\":\"raw10.denied\",\"type\":\"STRING\",\"value\":\"denied\"}"))
        .andExpect(status().isForbidden());
  }
  @Test void readAndWriteAliasesRemainSeparateRawDelegationBounds() throws Exception {
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
    return jwt().jwt(j -> j.subject("operator").claim("scope",raw)
        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
        .claim("enterprise_capabilities",Map.of("ent-acme",List.of("system.parameter.read","system.parameter.write"))))
        .authorities(new SimpleGrantedAuthority("SCOPE_"+authority));
  }
}
