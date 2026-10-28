package com.abluepoint.summer.sample.delegation.domain;
import java.util.List;
import java.util.Map;
/** Scope attenuation and lifetime limits for delegated authority. */
public final class DelegationScopeRules {
 private DelegationScopeRules() {}
 public static boolean subset(List<String> requested, List<String> granted) { return granted == null || requested.stream().allMatch(x -> granted.stream().anyMatch(y -> y.equals(x) || y.equals("*"))); }
 public static int risk(String risk) { return Map.of("LOW",1,"MEDIUM",2,"HIGH",3,"CRITICAL",4).getOrDefault(risk.toUpperCase(),99); }
 public static long grantLifetime(Long seconds) { return seconds == null ? 3600 : Math.max(60, Math.min(86400, seconds)); }
 public static long approvalLifetime(Long seconds) { return seconds == null ? 900 : Math.max(60, Math.min(3600, seconds)); }
 public static String decision(String action) { return action.equals("APPROVE") ? "APPROVED" : action.equals("REJECT") ? "REJECTED" : "WITHDRAWN"; }
}
