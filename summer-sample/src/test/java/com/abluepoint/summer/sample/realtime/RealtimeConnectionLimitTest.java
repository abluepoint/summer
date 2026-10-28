package com.abluepoint.summer.sample.realtime;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeAccess;
import com.abluepoint.summer.sample.realtime.domain.RealtimeConnectionLimitException;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeWebSocketHub;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RealtimeConnectionLimitTest {
    @Test void simultaneousConnectionsCannotExceedTheTenantLimit() throws Exception {
        var hub = new RealtimeWebSocketHub(new ObjectMapper(), mock(RealtimeAccess.class), mock(org.springframework.beans.factory.ObjectProvider.class));
        var start = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        var rejected = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(16)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 64; i++) {
                futures.add(executor.submit(() -> {
                    var session = mock(WebSocketSession.class);
                    when(session.getAttributes()).thenReturn(new java.util.HashMap<>(Map.of("enterpriseId", "ent-acme",
                            "sessionActive", (BooleanSupplier) () -> true)));
                    start.await(5, TimeUnit.SECONDS);
                    try { hub.register("ent-acme", session); accepted.incrementAndGet(); }
                    catch (RealtimeConnectionLimitException full) { rejected.incrementAndGet(); }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(15, TimeUnit.SECONDS);
            assertThat(accepted).hasValue(32);
            assertThat(rejected).hasValue(32);
            assertThat(hub.status("ent-acme").connections()).isEqualTo(32);
        } finally { hub.shutdown(); }
    }
}
