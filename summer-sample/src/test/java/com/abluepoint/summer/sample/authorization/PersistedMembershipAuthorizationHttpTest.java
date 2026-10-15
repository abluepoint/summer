package com.abluepoint.summer.sample.authorization;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties="summer.sample.authorization.legacy-scope-compatibility=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PersistedMembershipAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    @Test void theSameOldJwtLosesTaskAndDictionaryAccessAfterMemberOrEnterpriseRevocation() throws Exception {
        String subject="membership-revocation";
        members.provision("ent-acme", "member-revocation", subject, "Revocation fixture", "revocation@example.test");
        jdbc.update("insert into summer_authorization_capability(capability_id,enterprise_id,scope,code,resource,action,risk_level,status,version,created_at,updated_at) values ('revocation-task','ent-acme','ENTERPRISE','enterprise.tasks.read','GET:/api/v1/enterprises/{enterpriseId}/tasks','read','LOW','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),('revocation-dict','ent-acme','ENTERPRISE','platform.dictionary.read','dictionaries','read','LOW','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,capability_ids,capability_codes,version,created_at,updated_at) values ('revocation-role','ent-acme','Read','revocation',false,'ACTIVE',?,'enterprise.tasks.read',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "revocation-task\nrevocation-dict");
        jdbc.update("insert into summer_authorization_user_binding(binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values ('revocation-binding','ent-acme',?,'[\"revocation-role\"]','[]',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",subject);
        var oldJwt=jwt().jwt(j -> j.subject(subject).claim("summer_managed_grants",true)
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                .claim("enterprise_capabilities",Map.of("ent-acme",List.of("enterprise.tasks.read","platform.dictionary.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.tasks.read"),new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"));
        List<String> paths=List.of("/api/v1/enterprises/ent-acme/tasks","/api/v1/enterprises/ent-acme/dictionaries");
        for(String path:paths) mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        jdbc.update("update summer_enterprise_member set status='DISABLED' where subject=?",subject);
        for(String path:paths) mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
        jdbc.update("update summer_enterprise_member set status='ACTIVE' where subject=?",subject);
        for(String path:paths) mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        jdbc.update("delete from summer_enterprise_member where subject=?",subject);
        for(String path:paths) mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
        members.provision("ent-acme", "member-revocation-restored", subject, "Restored explicitly", "revocation@example.test");
        jdbc.update("update summer_enterprise set status='FROZEN' where enterprise_id='ent-acme'");
        for(String path:paths) mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
    }
}
