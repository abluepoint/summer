package com.abluepoint.summer.sample.realtime.domain;
import java.time.Instant;
import java.util.Map;
public final class RealtimeMessageRules {
 private RealtimeMessageRules() {}
 public static void validate(String clientMessageId, String recipient, Map<String,Object> payload, Integer ttlSeconds) {
  if (blank(clientMessageId) || !clientMessageId.matches("[A-Za-z0-9._:-]{1,128}")) throw new RealtimeMessageValidationException("clientMessageId is required and must be a safe identifier");
  if (payload == null || payload.isEmpty()) throw new RealtimeMessageValidationException("payload must not be empty");
  if (ttlSeconds != null && (ttlSeconds < 1 || ttlSeconds > 24*60*60)) throw new RealtimeMessageValidationException("ttlSeconds is outside the supported range");
  if (!blank(recipient) && recipient.trim().length() > 128) throw new RealtimeMessageValidationException("recipient is too long");
 }
 public static void requirePayloadSize(int bytes) { if (bytes > 64*1024) throw new RealtimeMessageValidationException("Message payload is too large"); }
 public static boolean mayAcknowledge(String status) { return !"ACKED".equals(status); }
 public static long cursor(String raw) {
  if (raw == null || raw.isBlank()) return 0;
  try { long value=Long.parseLong(raw.trim()); if(value<0) throw new NumberFormatException(); return value; }
  catch (NumberFormatException e) { throw new RealtimeMessageCursorException(); }
 }
 public static boolean validClaim(String enterpriseId, Instant expiresAt, boolean member, boolean capability, boolean scope, Instant now) {
  return !blank(enterpriseId) && expiresAt != null && expiresAt.isAfter(now) && member && capability && scope;
 }
 private static boolean blank(String value) { return value == null || value.isBlank(); }
}
