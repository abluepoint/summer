package com.abluepoint.summer.sample.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeConnectionTimeoutTest {
    @Test void idleClientIsClosedAndRemovedWithoutWaitingForAnotherFrame() throws Exception {
        verifyTimeout(false, new CloseStatus(1001, "idle timeout"));
    }

    @Test void stalledSendIsClosedByAnIndependentWatchdog() throws Exception {
        verifyTimeout(true, CloseStatus.SERVER_ERROR);
    }

    private void verifyTimeout(boolean stalledSend, CloseStatus expected) throws Exception {
        var hub = new RealtimeWebSocketHub(new ObjectMapper(), mock(RealtimeAccess.class), mock(org.springframework.beans.factory.ObjectProvider.class));
        var session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>(Map.of("enterpriseId", "ent-acme", "sessionActive", (BooleanSupplier) () -> true));
        when(session.getAttributes()).thenReturn(attributes); when(session.isOpen()).thenReturn(true);
        try {
            hub.register("ent-acme", session);
            attributes.put(stalledSend ? "sendStarted" : "lastClientActivity", System.nanoTime() - TimeUnit.SECONDS.toNanos(70));
            ReflectionTestUtils.invokeMethod(hub, "expireIdleConnections");
            verify(session).close(expected); verify(session, never()).sendMessage(any());
            org.assertj.core.api.Assertions.assertThat(hub.status("ent-acme").connections()).isZero();
        } finally { hub.shutdown(); }
    }
}
