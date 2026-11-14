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
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources interfaces;
    private BusinessManagedAuthorizationTestFixture managed;
    private String previousEnterpriseStatus;
    @org.junit.jupiter.api.AfterEach void releaseCurrentBusinessFixture() {
        if (previousEnterpriseStatus != null)
            jdbc.update("update summer_enterprise set status=? where enterprise_id='ent-acme'", previousEnterpriseStatus);
        jdbc.update("delete from summer_enterprise_member where enterprise_id='ent-acme' and member_id='member-revocation-restored' and subject='membership-revocation'");
        if (managed != null) managed.close();
    }
    @Test void theSameOldJwtLosesTaskAndDictionaryAccessAfterMemberOrEnterpriseRevocation() throws Exception {
        String subject="membership-revocation";
        previousEnterpriseStatus = jdbc.queryForList("select status from summer_enterprise where enterprise_id='ent-acme'", String.class).stream().findFirst().orElse(null);
        managed = new BusinessManagedAuthorizationTestFixture(jdbc, transactions, interfaces);
        managed.configuration("ent-acme", "dictionaries", false, subject);
        managed.interfaces("ENTERPRISE:ent-acme", subject, java.util.Set.of("tasks"));
        var oldJwt=jwt().jwt(j -> j.subject(subject).claim("scope", "enterprise.tasks.read platform.dictionary.read").claim("summer_managed_grants",true)
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
