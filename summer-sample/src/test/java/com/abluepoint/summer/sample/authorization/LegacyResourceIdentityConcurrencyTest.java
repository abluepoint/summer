package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Independent identity/constraint seam: a resolved resource must never change legacy permissions in flight. */
@SpringBootTest
class LegacyResourceIdentityConcurrencyTest {
    @Autowired LegacyResourceAdapter resources;
    @Autowired UnifiedAuthorizationService authorization;
    @Autowired AuthorizationCapabilityRepository capabilities;
    @Autowired AuthorizationRoleRepository roles;
    @Autowired AuthorizationUserBindingRepository bindings;

    @Test void competingWeakAliasCannotRebindAnAlreadyResolvedStrongResource() throws Exception {
        capabilities.saveAndFlush(new AuthorizationCapabilityEntity("weak-alias-cap", "ent-acme", "ENTERPRISE",
                "alias.weak", "alias.weak", "invoke", "NORMAL", "ACTIVE", 1));
        roles.saveAndFlush(new AuthorizationRoleEntity("weak-alias-role", "ent-acme", "Weak only", "weak-only",
                false, "ACTIVE", "weak-alias-cap", "alias.weak", null, 1));
        bindings.saveAndFlush(new AuthorizationUserBindingEntity("weak-alias-binding", "ent-acme", "alias-reader",
                "[\"weak-alias-role\"]", "[]", 1));
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("alias-reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
        var principal = new JwtAuthenticationToken(jwt);
        var resolved = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var original = executor.submit(() -> {
                var resource = resources.register("constraint-fixture", "EXTENSION", "GET", "/strict-alias", Map.of(), "alias.strong");
                resolved.countDown();
                if (!attempted.await(10, TimeUnit.SECONDS)) throw new AssertionError("competing registration did not finish");
                return authorization.decide(new ResourceAuthorization.Request(ResourceAuthorization.Domain.enterprise("ent-acme"),
                        principal, resource, "alias-race"));
            });
            var competing = executor.submit(() -> {
                if (!resolved.await(10, TimeUnit.SECONDS)) throw new AssertionError("original resource did not resolve");
                try {
                    resources.register("constraint-fixture", "EXTENSION", "GET", "/strict-alias", Map.of(), "alias.weak");
                    return false;
                } catch (AccessDeniedException conflict) { return true; }
                finally { attempted.countDown(); }
            });
            assertThat(competing.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(original.get(15, TimeUnit.SECONDS).allowed()).isFalse();
        }
    }
}
