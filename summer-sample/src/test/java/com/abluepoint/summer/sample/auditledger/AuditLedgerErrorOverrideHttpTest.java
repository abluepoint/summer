package com.abluepoint.summer.sample.auditledger;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class, AuditLedgerErrorOverrideHttpTest.StatusOverride.class})
class AuditLedgerErrorOverrideHttpTest {
    @Autowired MockMvc mvc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture fixture;
    @org.junit.jupiter.api.BeforeEach void authorizeExactValidationHandler() {
        fixture.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"),
            "audit-admin", (handler, path, method) -> method.equals("GET") && path.equals("/api/v1/enterprises/{enterpriseId}/audit-events:query"));
    }

    @Test
    void auditValidationUsesTheApplicationStatusPolicyAndKeepsItsLiteralMessage() throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-acme/audit-events:query")
                        .queryParam("page", "-1")
                        .header("X-Request-Id", "audit-policy")
                        .with(jwt().jwt(token -> token.subject("audit-admin").claim("scope", "enterprise.audit.read")
                                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.audit.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_INVALID"))
                .andExpect(jsonPath("$.error.message").value("page must be non-negative and size must be between 1 and 100"))
                .andExpect(jsonPath("$.requestId").value("audit-policy"))
                .andExpect(header().string("X-Request-Id", "audit-policy"));
    }

    @TestConfiguration
    static class StatusOverride {
        @Bean ErrorStatusResolver auditStatusPolicy() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> "IAM_AUDIT_INVALID".equals(code.code())
                    ? HttpStatus.UNPROCESSABLE_CONTENT : defaults.resolve(code);
        }
    }
}
