package com.abluepoint.summer.sample.realtime.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageCreateCommand;
public record RealtimeMessageCreateRequest(String clientMessageId, String recipient, Map<String, Object> payload,
                                    Integer ttlSeconds) { public RealtimeMessageCreateCommand toCommand() { return new RealtimeMessageCreateCommand(clientMessageId,recipient,payload,ttlSeconds); } }
