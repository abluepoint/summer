package com.abluepoint.summer.sample.resource;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.function.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class FunctionalResourceDiscoveryTest {
    @Test void opaqueSiblingDoesNotEraseDiscoverableRoutesOrExecuteHandlers() {
        var invoked = new AtomicBoolean();
        HandlerFunction<ServerResponse> handler = request -> { invoked.set(true); return ServerResponse.ok().build(); };
        RequestPredicate opaque = request -> true;
        var router = RouterFunctions.route().GET("/api/known", handler).route(opaque, handler).GET("/api/after", handler).build();
        var incomplete = new AtomicBoolean();
        assertThat(FunctionalResourceDiscovery.discover(router, () -> incomplete.set(true)).stream().map(InterfaceResourceDescriptor::normalizedPath).toList())
                .contains("/api/known", "/api/after");
        assertThat(invoked).isFalse();
        assertThat(incomplete).isTrue();
    }
}
