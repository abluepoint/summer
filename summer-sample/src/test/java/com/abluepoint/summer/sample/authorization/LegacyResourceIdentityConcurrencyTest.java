package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Retired aliases cannot establish or replace a resource identity, even under concurrent calls. */
@SpringBootTest
class LegacyResourceIdentityConcurrencyTest {
    @Autowired LegacyResourceAdapter resources;
    @Autowired JdbcTemplate db;

    @Test void competingWeakAliasCannotRebindAnAlreadyResolvedStrongResource() throws Exception {
        long before = db.queryForObject("select count(*) from summer_authorization_resource_identity", Long.class);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var strong = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("registration barrier timeout");
                return assertThrows(AccessDeniedException.class, () -> resources.register(
                    "constraint-fixture", "EXTENSION", "GET", "/strict-alias", Map.of(), "alias.strong"));
            });
            var weak = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("registration barrier timeout");
                return assertThrows(AccessDeniedException.class, () -> resources.register(
                    "constraint-fixture", "EXTENSION", "GET", "/strict-alias", Map.of(), "alias.weak"));
            });
            start.countDown();
            assertThat(strong.get(15, TimeUnit.SECONDS).getMessage()).contains("retired");
            assertThat(weak.get(15, TimeUnit.SECONDS).getMessage()).contains("retired");
        }
        assertThat(db.queryForObject("select count(*) from summer_authorization_resource_identity", Long.class)).isEqualTo(before);
    }
}
