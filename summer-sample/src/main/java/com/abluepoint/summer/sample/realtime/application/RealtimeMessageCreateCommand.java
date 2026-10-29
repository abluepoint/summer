package com.abluepoint.summer.sample.realtime.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RealtimeMessageCreateCommand(String clientMessageId, String recipient, Map<String, Object> payload,
                                    Integer ttlSeconds) {}
