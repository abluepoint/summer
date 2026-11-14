package com.abluepoint.summer.sample.delegation;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:delegation-error-policy;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({DelegationErrorPolicyHttpTest.TransportPolicy.class,CurrentManagedAuthorizationTestFixture.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DelegationErrorPolicyHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.delegation.DelegationController")
                && java.util.Set.of("create POST /api/v1/enterprises/{e}/agents/{a}/delegations")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "user", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.delegation.DelegationController")
                && java.util.Set.of("approval POST /internal/v1/mcp/approvals")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mvc;

    @Test void validationUsesTheApplicationResolverAndKeepsTheLiteralMessage() throws Exception {
        mvc.perform(post("/api/v1/enterprises/ent-acme/agents/agent-1/delegations")
                        .with(jwt().jwt(token -> token.claim("scope", "enterprise.agents.delegations.write").subject("admin")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.agents.delegations.write")))
                        .header("Idempotency-Key", "delegation-policy-error")
                        .header("X-Request-Id", "delegation-policy-request")
                        .header("Accept-Language", "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Request-Id", "delegation-policy-request"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_DELEGATION_INVALID"))
                .andExpect(jsonPath("$.error.message").value("scopeType, mcpServerId and toolPatterns are required"))
                .andExpect(jsonPath("$.error.violations").isEmpty())
                .andExpect(jsonPath("$.requestId").value("delegation-policy-request"));
    }

    @Test void nativeBusinessDenialUsesTheApplicationResolverWithoutLosingItsCause() throws Exception {
        mvc.perform(post("/internal/v1/mcp/approvals").with(jwt().jwt(j -> j.subject("user")
                        .claim("scope", "enterprise.mcp.approvals.write")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.mcp.approvals.write")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-other\"}")
                        .header("Idempotency-Key", "native-denial").header("X-Request-Id", "native-delegation-denial"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Delegation capability denied"))
                .andExpect(header().string("X-Request-Id", "native-delegation-denial"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                        .isInstanceOfSatisfying(com.abluepoint.summer.common.error.SummerException.class,
                                failure -> org.assertj.core.api.Assertions.assertThat(failure.getCause())
                                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportPolicy {
        @Bean @Primary ErrorStatusResolver applicationStatusResolver() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> code.category() == ErrorCategory.INVALID_ARGUMENT || code.category() == ErrorCategory.FORBIDDEN
                    ? HttpStatus.valueOf(422) : defaults.resolve(code);
        }
    }
}
