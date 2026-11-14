package com.abluepoint.summer.sample.organization;

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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:organization-error-policy;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({OrganizationErrorPolicyHttpTest.TransportPolicy.class, CurrentManagedAuthorizationTestFixture.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationErrorPolicyHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("create POST /api/v1/enterprises/{enterpriseId}/org-units")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mvc;

    @Test void validationUsesTheApplicationResolverAndKeepsTheLiteralMessage() throws Exception {
        mvc.perform(post("/api/v1/enterprises/ent-acme/org-units")
                        .with(jwt().jwt(token -> token.subject("admin").claim("scope", "enterprise.organization.read enterprise.organization.write")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.write")))
                        .header("Idempotency-Key", "organization-policy-error")
                        .header("X-Request-Id", "organization-policy-request")
                        .header("Accept-Language", "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Request-Id", "organization-policy-request"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_ORGANIZATION_INVALID"))
                .andExpect(jsonPath("$.error.message").value("name and code are required"))
                .andExpect(jsonPath("$.error.violations").isEmpty())
                .andExpect(jsonPath("$.requestId").value("organization-policy-request"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportPolicy {
        @Bean @Primary ErrorStatusResolver applicationStatusResolver() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> code.category() == ErrorCategory.INVALID_ARGUMENT ? HttpStatus.valueOf(422) : defaults.resolve(code);
        }
    }
}
