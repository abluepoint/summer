package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:release-revocation;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
class ReleasePermissionRevocationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CurrentManagedAuthorizationTestFixture current;

    @Test
    void oldManagedTokenLosesReadAndWriteAfterReleaseOperatorRoleIsRevoked() throws Exception {
        var domain = Domain.platform();
        String subject = "release-" + UUID.randomUUID();
        String role = grant(domain, subject);
        var oldToken = jwt().jwt(claims -> claims.subject(subject).claim("summer_managed_grants", true)
                        .claim("summer_identity_source", "local-user").claim("release_operator", true)
                        .claim("platform_capabilities", java.util.List.of("internal.release.read", "internal.release.write"))
                        .claim("scope", "internal.release.read internal.release.write"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"), new SimpleGrantedAuthority("SCOPE_internal.release.write"));
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken)).andExpect(status().isOk());
        current.revoke(domain, subject, role);
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken).header("X-Request-Id", "release-revoked"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").value("release-revoked"));
        mvc.perform(post("/internal/v1/release/gates:validate").with(oldToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"candidate\",\"dryRun\":true}"))
                .andExpect(status().isForbidden());
        grant(domain, subject);
        mvc.perform(get("/internal/v1/release/readiness").with(oldToken)).andExpect(status().isOk());
    }

    private String grant(Domain domain, String subject) {
        return current.grantHandlers(domain, subject, (handler, path, method) -> handler.getBeanType() == ReleaseGateController.class
                && java.util.Set.of("/internal/v1/release/performance-budgets", "/internal/v1/release/readiness",
                        "/internal/v1/release/performance-benchmarks", "/internal/v1/release/gates:validate",
                        "/internal/v1/release/rollback:dry-run", "/internal/v1/release/failure-drills").contains(path));
    }
}
