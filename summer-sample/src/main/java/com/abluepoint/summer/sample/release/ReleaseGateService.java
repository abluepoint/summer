package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
final class ReleaseGateService {
    private static final String CURRENT = "aict-17";
    private static final List<String> DEFAULT_CHECKS = List.of("format", "lint", "typecheck", "unit", "bff-contract", "build", "e2e");
    private final Map<String, Object> receipts = new HashMap<>();
    private final RuntimeReleaseChecks runtimeChecks;

    private final AuthorizationService authorization;
    private final RequestIdAccessor requestId;

    ReleaseGateService(RuntimeReleaseChecks runtimeChecks, AuthorizationService authorization, RequestIdAccessor requestId) {
        this.runtimeChecks = runtimeChecks;
        this.authorization = authorization;
        this.requestId = requestId;
    }

    synchronized PerformanceBudgetView budgets(Authentication auth) {
        require(auth, "internal.release.read");
        return new PerformanceBudgetView("NON_PRODUCTION_IN_MEMORY", new PerformanceBudgets(500, 20, 30, 50, 20, 100), true);
    }

    synchronized PerformanceBenchmarkView benchmark(PerformanceBenchmarkRequest request, String key, Authentication auth) {
        require(auth, "internal.release.write");
        requireKey(key);
        String fingerprint = String.valueOf(request);
        Object prior = receipts.get("benchmark:" + key);
        if (prior != null) {
            Receipt receipt = (Receipt) prior;
            if (!receipt.fingerprint.equals(fingerprint)) throw new ReleaseIdempotencyConflictException("Idempotency key reused");
            return (PerformanceBenchmarkView) receipt.value;
        }
        if (request == null || blank(request.operation()) || request.latenciesMs() == null || request.latenciesMs().isEmpty()
                || request.elapsedMs() <= 0 || request.successful() < 0 || request.failed() < 0) {
            throw new ReleaseValidationException("operation, samples, elapsedMs and counts are required");
        }
        long budget = budgetFor(request.operation());
        List<Long> sorted = request.latenciesMs().stream().filter(Objects::nonNull).filter(v -> v >= 0).sorted().toList();
        if (sorted.isEmpty()) throw new ReleaseValidationException("latenciesMs must contain non-negative values");
        int rank = (int) Math.ceil(sorted.size() * 0.95d) - 1;
        long p95 = sorted.get(Math.max(0, Math.min(rank, sorted.size() - 1)));
        double throughput = request.successful() * 1000d / request.elapsedMs();
        String reason = request.failed() > 0 ? "SAMPLE_FAILURES" : (p95 > budget ? "P95_BUDGET_EXCEEDED" : null);
        PerformanceBenchmarkView out = new PerformanceBenchmarkView(request.operation(), sorted.size(), p95,
                throughput, budget, reason == null, request.failed(), reason, "NON_PRODUCTION_IN_MEMORY");
        receipts.put("benchmark:" + key, new Receipt(fingerprint, out));
        return out;
    }

    synchronized ReadinessView readiness(Authentication auth) {
        require(auth, "internal.release.read");
        List<ReadinessCheck> checks = runtimeChecks.snapshot();
        return new ReadinessView(checks.stream().allMatch(ReadinessCheck::passed) ? "READY" : "NOT_READY", checks, true);
    }

    synchronized ReleaseGateView validate(ReleaseGateRequest request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key);
        String fp = String.valueOf(request);
        Object prior = receipts.get(key);
        if (prior != null) {
            Receipt receipt = (Receipt) prior;
            if (!receipt.fingerprint.equals(fp)) throw new ReleaseIdempotencyConflictException("Idempotency key reused");
            return (ReleaseGateView) receipt.value;
        }
        if (request == null || request.releaseVersion() == null || !Boolean.TRUE.equals(request.dryRun())) throw new ReleaseValidationException("dryRun is required");
        List<String> checks = request.requiredChecks() == null || request.requiredChecks().isEmpty() ? DEFAULT_CHECKS : request.requiredChecks();
        List<ReleaseGate> gates = checks.stream().distinct().map(c -> new ReleaseGate(c, false, false, "External execution evidence required")).toList();
        ReleaseGateView out = new ReleaseGateView("NOT_VERIFIED", request.releaseVersion(), true, gates); receipts.put(key, new Receipt(fp, out)); return out;
    }

    synchronized RollbackDryRunView rollback(RollbackDryRunRequest request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key); String fingerprint = String.valueOf(request); Object prior = receipts.get(key); if (prior != null) { Receipt receipt = (Receipt) prior; if (!receipt.fingerprint.equals(fingerprint)) throw new ReleaseIdempotencyConflictException("Idempotency key reused"); return (RollbackDryRunView) receipt.value; }
        if (request == null || blank(request.releaseVersion()) || blank(request.targetVersion())) throw new ReleaseValidationException("releaseVersion and targetVersion are required");
        RollbackDryRunView out = new RollbackDryRunView(true, request.releaseVersion(), request.targetVersion(), CURRENT); receipts.put(key, new Receipt(fingerprint, out)); return out;
    }

    synchronized FailureDrillView drill(FailureDrillRequest request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key); String fingerprint = String.valueOf(request); Object prior = receipts.get(key); if (prior != null) { Receipt receipt = (Receipt) prior; if (!receipt.fingerprint.equals(fingerprint)) throw new ReleaseIdempotencyConflictException("Idempotency key reused"); return (FailureDrillView) receipt.value; }
        if (request == null || blank(request.component()) || !"UNAVAILABLE".equalsIgnoreCase(request.scenario())) throw new ReleaseValidationException("Only UNAVAILABLE failure scenario is supported");
        String component = request.component().toUpperCase(Locale.ROOT); FailureDrillView out;
        if ("IAM".equals(component)) out = new FailureDrillView(component, "UNAVAILABLE", "DENY", null, false, false, "REQUIRED");
        else if ("CONNECTION_SERVICE".equals(component)) out = new FailureDrillView(component, "UNAVAILABLE", null, "EXECUTION_UNKNOWN", false, false, "REQUIRED");
        else throw new ReleaseValidationException("Unsupported component");
        receipts.put(key, new Receipt(fingerprint, out)); return out;
    }

    private void require(Authentication auth, String scope) {
        if (!(auth instanceof JwtAuthenticationToken jwt)
                || !Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean("release_operator"))
                || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + scope).equals(a.getAuthority()))) {
            throw new AccessDeniedException("Release operator denied");
        }
        // Local and managed grants can change after token issuance. Reuse the same
        // persistent role, explicit-deny and membership decision as business APIs.
        if (Boolean.TRUE.equals(jwt.getToken().getClaims().get("summer_managed_grants"))
                || "local-user".equals(jwt.getToken().getClaimAsString("summer_identity_source"))) {
            if (!persistedGrant(jwt, "internal.release.read")
                    || (!"internal.release.read".equals(scope) && !persistedGrant(jwt, scope))) {
                throw new AccessDeniedException("Release operator denied");
            }
        }
    }

    private boolean persistedGrant(JwtAuthenticationToken jwt, String permission) {
        return authorization.platformDecision(jwt, permission, requestId.currentRequestId()).allowed();
    }

    private static void requireKey(String key) { if (key == null || key.isBlank()) throw new ReleaseValidationException("Idempotency-Key is required"); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static long budgetFor(String operation) {
        return switch (operation) {
            case "managementList" -> 500;
            case "cachedAuthorization" -> 20;
            case "mcpDecision" -> 30;
            case "authorizationDecision" -> 50;
            case "permitClaim" -> 20;
            case "auditWrite" -> 100;
            default -> throw new ReleaseValidationException("Unsupported benchmark operation");
        };
    }
    private record Receipt(String fingerprint, Object value) {}
}
