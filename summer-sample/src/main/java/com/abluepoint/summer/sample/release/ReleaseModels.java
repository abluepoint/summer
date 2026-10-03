package com.abluepoint.summer.sample.release;

import java.util.List;

record PerformanceBudgetView(String mode, PerformanceBudgets budgets, boolean excludesExternalToolExecution) {}
record PerformanceBudgets(
        int managementListP95Ms,
        int cachedAuthorizationP95Ms,
        int mcpDecisionP95Ms,
        int authorizationDecisionP95Ms,
        int permitClaimP95Ms,
        int auditWriteP95Ms) {}
record ReadinessCheck(String name, boolean passed, String detail) {}
record ReadinessView(String status, List<ReadinessCheck> checks, boolean rollbackDryRunSupported) {}
record ReleaseGateRequest(String releaseVersion, Boolean dryRun, List<String> requiredChecks) {}
record ReleaseGate(String name, boolean executed, boolean passed, String evidence) {}
record ReleaseGateView(String decision, String releaseVersion, boolean dryRun, List<ReleaseGate> gates) {}
record RollbackDryRunRequest(String releaseVersion, String targetVersion, String reason) {}
record RollbackDryRunView(boolean dryRun, String wouldSwitchFrom, String wouldSwitchTo, String currentRelease) {}
record FailureDrillRequest(String component, String scenario) {}
record FailureDrillView(String component, String scenario, String authorizationDecision, String executionStatus, boolean retry, boolean automaticRetry, String auditEvidence) {}
record PerformanceBenchmarkRequest(String operation, List<Long> latenciesMs, long elapsedMs, int successful, int failed) {}
record PerformanceBenchmarkView(String operation, int sampleCount, long p95Ms, double throughputPerSecond,
                                long budgetMs, boolean budgetPassed, int failed, String failureReason, String mode) {}
