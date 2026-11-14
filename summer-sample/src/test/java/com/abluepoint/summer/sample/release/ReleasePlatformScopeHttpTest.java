package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:release-platform-scope;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
class ReleasePlatformScopeHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired CurrentManagedAuthorizationTestFixture current;

    @ParameterizedTest
    @ValueSource(strings = {"internal.release.read", "internal.release.write"})
    void anEnterprisePermissionWithTheSameCodeCannotRestoreARevokedPlatformOperation(String revoked) throws Exception {
        String subject = "release-scope-" + UUID.randomUUID();
        String read = grant(subject, false), write = grant(subject, true);
        var token = token(subject);
        releaseRequest("internal.release.read", token).andExpect(status().isOk());
        releaseRequest("internal.release.write", token).andExpect(status().isOk());
        var tenant = Domain.enterprise("ent-acme");
        String shadow = current.grantHandlers(tenant, subject, subject + (revoked.endsWith("read") ? "-read" : "-write"),
                (handler, path, method) -> handler.getBeanType().getName().equals(
                "com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController")
                && path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("GET"));
        org.assertj.core.api.Assertions.assertThat(db.queryForObject(
                "select count(*) from summer_permission where domain_key in (?,?) and code=? and status='ACTIVE'",
                Integer.class, Domain.platform().key(), tenant.key(),
                subject + (revoked.endsWith("read") ? "-read" : "-write"))).isEqualTo(2);
        current.revoke(Domain.platform(), subject, revoked.endsWith("read") ? read : write);
        releaseRequest(revoked, token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("release-scope-revocation"));
        releaseRequest(revoked.endsWith("read") ? "internal.release.write" : "internal.release.read", token)
                .andExpect(status().isOk());
    }

    @Test
    void platformGrantsAreIndependentOfEnterpriseMembershipAndNeverComposedFromEnterpriseRoles() throws Exception {
        String subject = "release-domain-" + UUID.randomUUID();
        var tenant = Domain.enterprise("ent-acme");
        current.grantHandlers(tenant, subject, (handler, path, method) -> handler.getBeanType().getName().equals(
                "com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController")
                && path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("GET"));
        var token = token(subject);
        releaseRequest("internal.release.read", token).andExpect(status().isForbidden());
        releaseRequest("internal.release.write", token).andExpect(status().isForbidden());
        grant(subject, false); grant(subject, true);
        releaseRequest("internal.release.read", token).andExpect(status().isOk());
        releaseRequest("internal.release.write", token).andExpect(status().isOk());
        db.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?",tenant.id(),subject);
        db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?",tenant.key());
        releaseRequest("internal.release.read", token).andExpect(status().isOk());
        releaseRequest("internal.release.write", token).andExpect(status().isOk());
    }

    private String grant(String subject, boolean write) {
        return current.grantHandlers(Domain.platform(), subject, subject + (write ? "-write" : "-read"),
                (handler, path, method) -> handler.getBeanType() == ReleaseGateController.class
                && (write ? method.equals("POST") : method.equals("GET"))
                && java.util.Set.of("/internal/v1/release/performance-budgets", "/internal/v1/release/readiness",
                        "/internal/v1/release/performance-benchmarks", "/internal/v1/release/gates:validate",
                        "/internal/v1/release/rollback:dry-run", "/internal/v1/release/failure-drills").contains(path));
    }
    private static RequestPostProcessor token(String subject) {
        return jwt().jwt(claims -> claims.subject(subject).claim("summer_managed_grants",true)
                        .claim("summer_identity_source","local-user").claim("release_operator",true)
                        .claim("platform_capabilities", List.of("internal.release.read","internal.release.write"))
                        .claim("scope","internal.release.read internal.release.write"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"),new SimpleGrantedAuthority("SCOPE_internal.release.write"));
    }
    private ResultActions releaseRequest(String permission, RequestPostProcessor token) throws Exception {
        if (permission.endsWith("read")) return mvc.perform(get("/internal/v1/release/readiness").with(token)
                .header("X-Request-Id","release-scope-revocation"));
        return mvc.perform(post("/internal/v1/release/gates:validate").with(token).header("X-Request-Id","release-scope-revocation")
                .header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseVersion\":\"scope-test\",\"dryRun\":true}"));
    }
}
