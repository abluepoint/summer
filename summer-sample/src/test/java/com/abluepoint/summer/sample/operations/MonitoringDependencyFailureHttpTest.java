package com.abluepoint.summer.sample.operations;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:monitoring-dependency-failure;MODE=MySQL;DB_CLOSE_DELAY=0",
        "summer.sample.authorization.legacy-scope-compatibility=false"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CurrentManagedAuthorizationTestFixture.class)
class MonitoringDependencyFailureHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CurrentManagedAuthorizationTestFixture current;
    @MockitoSpyBean RuntimeDiagnosticsService runtime;

    @Test
    void authoritativeStoreFailurePreservesDependencyClassificationWithoutExecutingBusiness() throws Exception {
        String subject = "monitor-fault-" + UUID.randomUUID();
        String path = "/internal/v1/diagnostics/runtime";
        String role = current.grantInstalled(Domain.platform(), subject,
                mapping -> mapping.source().equals("MONITORING") && mapping.method().equals("GET")
                        && mapping.path().equals(path)
                        && mapping.handler().startsWith(RuntimeDiagnosticsController.class.getName() + "@"));
        var authorized = jwt().jwt(token -> token.subject(subject).claim("scope", "platform.capabilities.read"))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject(subject).claim("scope", "openid"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject("missing-" + subject)
                        .claim("scope", "platform.capabilities.read"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).with(authorized)).andExpect(status().isOk());
        clearInvocations(runtime);

        jdbc.execute("alter table summer_authorization_domain rename to monitoring_unavailable_domain");
        try {
            String requestId = "monitor-dependency-" + UUID.randomUUID();
            var result = mvc.perform(get(path).with(authorized).header("X-Request-Id", requestId))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("X-Request-Id", requestId))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("SUMMER_DEPENDENCY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andReturn();
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("monitoring_unavailable_domain", "BadSqlGrammarException", "select engine_mode");
            verifyNoInteractions(runtime);
        } finally {
            jdbc.execute("alter table monitoring_unavailable_domain rename to summer_authorization_domain");
        }
        mvc.perform(get(path).with(authorized)).andExpect(status().isOk());
        current.revoke(Domain.platform(), subject, role);
        mvc.perform(get(path).with(authorized)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }
}
