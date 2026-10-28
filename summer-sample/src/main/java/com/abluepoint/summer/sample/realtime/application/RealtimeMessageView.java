package com.abluepoint.summer.sample.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record RealtimeMessageView(String messageId, String enterpriseId, String clientMessageId, String sender,
                           String recipient, Map<String, Object> payload, long sequence, String status,
                           Instant createdAt, Instant acknowledgedAt, boolean duplicate) {}

