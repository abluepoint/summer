package com.abluepoint.summer.sample.realtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record RealtimeMessageCreateRequest(String clientMessageId, String recipient, Map<String, Object> payload,
                                    Integer ttlSeconds) {}

record RealtimeMessageView(String messageId, String enterpriseId, String clientMessageId, String sender,
                           String recipient, Map<String, Object> payload, long sequence, String status,
                           Instant createdAt, Instant acknowledgedAt, boolean duplicate) {}

record RealtimeMessagePage(List<RealtimeMessageView> items, long latestSequence, boolean hasMore) {
    RealtimeMessagePage {
        items = List.copyOf(items);
    }
}

record RealtimeConnectionView(String enterpriseId, int connections, int maxConnections, String status) {}

record RealtimeMessagePublishedEvent(RealtimeMessageView message) {}

final class RealtimeMessageValidationException extends RuntimeException {
    RealtimeMessageValidationException(String message) { super(message); }
}

final class RealtimeMessageCursorException extends RuntimeException {
    RealtimeMessageCursorException() { super("Message cursor must be a non-negative integer"); }
}

final class RealtimeMessageNotFoundException extends RuntimeException {
    RealtimeMessageNotFoundException() { super("Message was not found"); }
}
