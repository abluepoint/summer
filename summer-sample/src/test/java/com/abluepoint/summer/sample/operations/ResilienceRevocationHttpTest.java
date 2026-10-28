package com.abluepoint.summer.sample.operations;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"summer.sample.authorization.legacy-scope-compatibility=false",
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResilienceRevocationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.infrastructure.MemberDirectory members;

    @ParameterizedTest
    @ValueSource(strings = {"/internal/v1/diagnostics/resilience"})
    void theSameOldJwtCannotReadAfterPersistedDenyOrRoleRemoval(String path) throws Exception {
        String id = UUID.randomUUID().toString();
        String subject = "monitor-" + id;
        members.provision("ent-acme", id, subject, "Monitoring fixture", "monitor@example.test");
        jdbc.update("""
                insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,
                  capability_ids,capability_codes,version,created_at,updated_at)
                values (?,'ent-acme','Monitoring',?,false,'ACTIVE','cap-platform-capabilities-read',
                  'platform.capabilities.read',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, id);
        jdbc.update("""
                insert into summer_authorization_user_binding(binding_id,enterprise_id,username,role_ids,
                  denied_capability_codes,version,created_at,updated_at)
                values (?,'ent-acme',?,?,'[]',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, subject, "[\"" + id + "\"]");
        var oldJwt = jwt().jwt(token -> token.subject(subject).claim("summer_managed_grants", true)
                        .claim("active_enterprise_id", "ent-acme")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("platform_capabilities", List.of("platform.capabilities.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        jdbc.update("update summer_authorization_user_binding set denied_capability_codes=? where binding_id=?",
                "[\"platform.capabilities.read\"]", id);
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        jdbc.update("update summer_authorization_user_binding set denied_capability_codes='[]',role_ids='[]' where binding_id=?", id);
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
    }
}
