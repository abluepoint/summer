package com.abluepoint.summer.sample.auditledger.domain;
import java.util.*;
/** Immutable evidence excludes reusable credentials and raw execution results recursively. */
public final class AuditEvidenceRules {
 private AuditEvidenceRules() {}
    public static void requireImmutable(Map<String,Object> existing, Map<String,Object> supplied) {
        if (!Objects.equals(existing, redact(supplied))) throw new AuditEventImmutableException("Event is immutable");
    }
    public static boolean completed(String status, String digest) {
        return digest != null && !"EXECUTION_UNKNOWN".equals(status);
    }
    public static boolean contradicts(String status, String previousDigest, String nextDigest) {
        return completed(status, previousDigest) && !previousDigest.equals(nextDigest);
    }
    public static Map<String,Object> redact(Map<String,Object> d){
        if (d == null) return Map.of();
        Map<String,Object> output = new LinkedHashMap<>();
        d.forEach((key, value) -> {
            if (value != null && !sensitiveKey(key)) output.put(key, redactValue(value));
        });
        return Map.copyOf(output);
    }
    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String,Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                if (item != null && !sensitiveKey(name)) nested.put(name, redactValue(item));
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(AuditEvidenceRules::redactValue).toList();
        return value;
    }
    private static boolean sensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("token") || normalized.contains("secret") || normalized.contains("password")
                || normalized.contains("privatekey") || normalized.contains("rawresult") || normalized.equals("authorization");
    }


}
