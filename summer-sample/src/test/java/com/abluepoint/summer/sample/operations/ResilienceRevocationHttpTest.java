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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class ResilienceRevocationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(strings = {"/internal/v1/diagnostics/resilience"})
    void theSameOldJwtCannotReadAfterPermissionOrRoleRemoval(String path) throws Exception {
        String id = UUID.randomUUID().toString();
        String subject = "monitor-" + id;
        var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform();
        String role = current.grantInstalled(domain, subject, mapping -> mapping.source().equals("MONITORING")
                && mapping.path().equals(path) && java.util.Set.of("GET", "HEAD").contains(mapping.method()));
        var oldJwt = jwt().jwt(token -> token.subject(subject).claim("summer_managed_grants", true)
                        .claim("scope", "platform.capabilities.read")
                        .claim("platform_capabilities", List.of("platform.capabilities.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));
        mvc.perform(get(path).with(oldJwt)).andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .as("Current diagnostics positive; resolved exception: %s", result.getResolvedException())
                        .isEqualTo(200));
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("update summer_permission set status='DISABLED' where domain_key=? and permission_id=?", domain.key(), role);
            jdbc.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", domain.key());
        });
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("update summer_permission set status='ACTIVE' where domain_key=? and permission_id=?", domain.key(), role);
            jdbc.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", domain.key());
        });
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        current.revoke(domain, subject, role);
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
    }
}
