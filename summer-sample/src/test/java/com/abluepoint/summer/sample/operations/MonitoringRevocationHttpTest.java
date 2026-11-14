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
class MonitoringRevocationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/prometheus", "/actuator/metrics", "/internal/v1/diagnostics/runtime"})
    void theSameOldJwtCannotReadAfterCurrentPermissionDisableOrRoleRemoval(String path) throws Exception {
        String subject = "monitor-" + UUID.randomUUID();
        var platform = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform();
        String roleId = current.grantInstalled(platform, subject,
                mapping -> mapping.source().equals("MONITORING") && mapping.method().equals("GET")
                        && mapping.path().equals(path)
                        && (mapping.handler().startsWith("com.abluepoint.summer.sample.operations.RuntimeDiagnosticsController@")
                            || mapping.handler().startsWith("org.springframework.boot.webmvc.actuate.endpoint.web.AbstractWebMvcEndpointHandlerMapping$OperationHandler@")));
        var oldJwt = jwt().jwt(token -> token.subject(subject).claim("scope", "platform.capabilities.read")
                        .claim("active_enterprise_id", "ent-acme")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("platform_capabilities", List.of("platform.capabilities.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        // Current interface grant and raw OAuth delegation remain independent.
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(subject).claim("scope", ""))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isForbidden());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("update summer_permission set status='DISABLED' where domain_key=? and permission_id=?",
                    platform.key(), roleId);
            jdbc.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", platform.key());
        });
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        // Restore only the permission created by this test, then independently remove its role.
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("update summer_permission set status='ACTIVE' where domain_key=? and permission_id=?",
                    platform.key(), roleId);
            jdbc.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", platform.key());
        });
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isOk());
        current.revoke(platform, subject, roleId);
        mvc.perform(get(path).with(oldJwt)).andExpect(status().isForbidden());
    }
}
