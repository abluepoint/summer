package com.abluepoint.summer.sample.identity;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:configured-bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.security.authorization-server.user.username=configured-bootstrap-admin"})
@Transactional
class ConfiguredBootstrapMembershipTest {
    @Autowired LocalConsoleIdentityClaims identities;
    @Autowired com.abluepoint.summer.sample.member.infrastructure.MemberDirectory members;
    @Autowired JdbcTemplate jdbc;
    @Test void configuredBootstrapUsesADurableMembershipAndDisablingItRemovesGrants() {
        String username = "configured-bootstrap-admin";
        assertThat(members.forSubject("ent-acme", username)).isPresent();
        var prior = Map.<String,Object>of("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")),
                "enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.write")),
                "platform_capabilities", List.of("platform.capabilities.write"));
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username, prior))).contains("enterprise.roles.write");
        jdbc.update("update summer_enterprise_member set status='DISABLED' where subject=?", username);
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username, prior))).isEmpty();
    }
}
