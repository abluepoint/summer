package com.abluepoint.summer.sample.release.domain;
import java.util.*;
public final class ReleaseGateRules {
 private static final List<String> DEFAULT_CHECKS = List.of("format","lint","typecheck","unit","bff-contract","build","e2e");
 private static final PerformanceBudgets BUDGETS = new PerformanceBudgets(500,20,30,50,20,100);
 private ReleaseGateRules() {}
 public static void requireReplay(String original,String supplied) { if(!original.equals(supplied)) throw new ReleaseIdempotencyConflictException("Idempotency key reused"); }
 public static PerformanceBudgets budgets() { return BUDGETS; }
 public static String readinessStatus(List<ReadinessCheck> checks) { return checks.stream().allMatch(ReadinessCheck::passed)?"READY":"NOT_READY"; }
 public static PerformanceAssessment assess(String operation, List<Long> latencies, long elapsedMs, int successful, int failed) {
  if (operation == null || operation.isBlank() || latencies == null || latencies.isEmpty() || elapsedMs<=0 || successful<0 || failed<0) throw new ReleaseValidationException("operation, samples, elapsedMs and counts are required");
  long budget=budgetFor(operation);
  List<Long> sorted=latencies.stream().filter(Objects::nonNull).filter(v->v>=0).sorted().toList();
  if(sorted.isEmpty()) throw new ReleaseValidationException("latenciesMs must contain non-negative values");
  int rank=(int)Math.ceil(sorted.size()*0.95d)-1;
  long p95=sorted.get(Math.max(0,Math.min(rank,sorted.size()-1)));
  String reason=failed>0?"SAMPLE_FAILURES":p95>budget?"P95_BUDGET_EXCEEDED":null;
  return new PerformanceAssessment(sorted.size(),p95,successful*1000d/elapsedMs,budget,reason);
 }
 public static List<ReleaseGate> gates(String version, Boolean dryRun, List<String> checks) {
  if(version == null || !Boolean.TRUE.equals(dryRun)) throw new ReleaseValidationException("dryRun is required");
  List<String> required=checks==null || checks.isEmpty()?DEFAULT_CHECKS:checks;
  return required.stream().distinct().map(c->new ReleaseGate(c,false,false,"External execution evidence required")).toList();
 }
 public static void requireRollback(String release, String target) { if(blank(release)||blank(target)) throw new ReleaseValidationException("releaseVersion and targetVersion are required"); }
 public static FailureComponent drillComponent(String component,String scenario) {
  if(blank(component)||!"UNAVAILABLE".equalsIgnoreCase(scenario)) throw new ReleaseValidationException("Only UNAVAILABLE failure scenario is supported");
  String normalized=component.toUpperCase(Locale.ROOT);
  if(!Set.of("IAM","CONNECTION_SERVICE").contains(normalized)) throw new ReleaseValidationException("Unsupported component");
  return FailureComponent.valueOf(normalized);
 }
 private static boolean blank(String value) { return value==null||value.isBlank(); }
 private static long budgetFor(String operation) { return switch(operation) {
  case "managementList" -> BUDGETS.managementListP95Ms(); case "cachedAuthorization" -> BUDGETS.cachedAuthorizationP95Ms(); case "mcpDecision" -> BUDGETS.mcpDecisionP95Ms();
  case "authorizationDecision" -> BUDGETS.authorizationDecisionP95Ms(); case "permitClaim" -> BUDGETS.permitClaimP95Ms(); case "auditWrite" -> BUDGETS.auditWriteP95Ms();
  default -> throw new ReleaseValidationException("Unsupported benchmark operation");
 }; }
}
