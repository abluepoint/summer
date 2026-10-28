package com.abluepoint.summer.sample.realtime;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeAccess;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageView;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeWebSocketHub;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageService;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessagePageView;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeDeliveryOrderTest {
    @Test void duplicateAndReorderedNotificationsDrainTheDurableStreamOnceInSequence() throws Exception {
        var service = mock(RealtimeMessageService.class);
        @SuppressWarnings("unchecked") ObjectProvider<RealtimeMessageService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(service);
        when(service.list(eq("ent-acme"), anyLong(), any())).thenAnswer(invocation -> {
            long after = invocation.getArgument(1);
            return new RealtimeMessagePageView(List.of(message(1), message(2), message(3)).stream()
                    .filter(message -> message.sequence() > after).toList(), 3, false);
        });
        var hub = new RealtimeWebSocketHub(new ObjectMapper(), mock(RealtimeAccess.class), provider);
        var session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        attributes.put("enterpriseId", "ent-acme"); attributes.put("after", 0L);
        attributes.put("sessionActive", (BooleanSupplier) () -> true);
        when(session.getAttributes()).thenReturn(attributes); when(session.isOpen()).thenReturn(true);
        var frames = new ArrayList<String>();
        doAnswer(invocation -> { frames.add(((TextMessage) invocation.getArgument(0)).getPayload()); return null; }).when(session).sendMessage(any());
        try {
            hub.register("ent-acme", session);
            hub.broadcast("ent-acme", Map.of("sequence", 3));
            assertThat(frames).isEmpty(); // subscribed, but connected must be the first frame
            hub.connected(session, Map.of("type", "connected"));
            hub.broadcast("ent-acme", Map.of("sequence", 2));
            hub.broadcast("ent-acme", Map.of("sequence", 3));
            assertThat(frames).hasSize(4);
            var mapper = new ObjectMapper();
            assertThat(mapper.readTree(frames.getFirst()).get("type").asText()).isEqualTo("connected");
            assertThat(frames.subList(1, 4).stream().map(frame -> mapper.readTree(frame).get("message").get("sequence").asLong()))
                    .containsExactly(1L, 2L, 3L);
        } finally { hub.shutdown(); }
    }

    private static RealtimeMessageView message(long sequence) {
        return new RealtimeMessageView("message-" + sequence, "ent-acme", "client-" + sequence,
                "sender", null, Map.of("text", "order fixture"), sequence, "SENT", Instant.now(), null, false);
    }
}
