package com.abluepoint.summer.sample.realtime;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeAccess;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeWebSocketHub;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RealtimeConnectionObservationTest {
    @Test
    @SuppressWarnings("unchecked")
    void gaugeCountsOnlyOpenConnectionsAcrossEnterprisesWithoutReturningIdentifiers() {
        var hub = new RealtimeWebSocketHub(new ObjectMapper(), mock(RealtimeAccess.class), mock(org.springframework.beans.factory.ObjectProvider.class));
        var first = session("one"); var second = session("two");
        try {
            hub.register("one", first); hub.register("two", second);
            assertThat(hub.activeConnectionCount()).isEqualTo(2);
            when(second.isOpen()).thenReturn(false);
            assertThat(hub.activeConnectionCount()).isEqualTo(1);
            hub.remove("one", first);
            assertThat(hub.activeConnectionCount()).isZero();
        } finally { hub.shutdown(); }
    }

    private WebSocketSession session(String enterprise) {
        var session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("enterpriseId", enterprise,
                "sessionActive", (BooleanSupplier) () -> true)));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
