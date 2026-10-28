package com.abluepoint.summer.sample.release.application;
import com.abluepoint.summer.sample.release.domain.PerformanceBudgets;
import com.abluepoint.summer.sample.release.domain.ReleaseIdempotencyConflictException;
import com.abluepoint.summer.sample.release.domain.ReleaseReadinessPort;
import com.abluepoint.summer.sample.release.domain.ReleaseGate;
import com.abluepoint.summer.sample.release.domain.ReleaseValidationException;
import com.abluepoint.summer.sample.release.domain.ReadinessCheck;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.*;
import com.abluepoint.summer.sample.release.domain.ReleaseGateRules;
import com.abluepoint.summer.sample.release.domain.PerformanceAssessment;

@Service
public final class ReleaseGateService {
    private static final String CURRENT = "aict-17";
    private final Map<String, Object> receipts = new HashMap<>();
    private final ReleaseReadinessPort runtimeChecks;

    private final AuthorizationService authorization;
    private final RequestIdAccessor requestId;

    public ReleaseGateService(ReleaseReadinessPort runtimeChecks, AuthorizationService authorization, RequestIdAccessor requestId) {
        this.runtimeChecks = runtimeChecks;
        this.authorization = authorization;
        this.requestId = requestId;
    }

    public synchronized PerformanceBudgetView budgets(Authentication auth) {
        require(auth, "internal.release.read");
        return new PerformanceBudgetView("NON_PRODUCTION_IN_MEMORY", ReleaseGateRules.budgets(), true);
    }

    public synchronized PerformanceBenchmarkView benchmark(PerformanceBenchmarkCommand request, String key, Authentication auth) {
        require(auth, "internal.release.write");
        requireKey(key);
        String fingerprint = String.valueOf(request);
        Object prior = receipts.get("benchmark:" + key);
        if (prior != null) {
            Receipt receipt = (Receipt) prior;
            ReleaseGateRules.requireReplay(receipt.fingerprint,fingerprint);
            return (PerformanceBenchmarkView) receipt.value;
        }
        if(request == null) throw new ReleaseValidationException("operation, samples, elapsedMs and counts are required");
        PerformanceAssessment result=ReleaseGateRules.assess(request.operation(),request.latenciesMs(),request.elapsedMs(),request.successful(),request.failed());
        PerformanceBenchmarkView out=new PerformanceBenchmarkView(request.operation(),result.sampleCount(),result.p95Ms(),result.throughputPerSecond(),result.budgetMs(),result.passed(),request.failed(),result.failureReason(),"NON_PRODUCTION_IN_MEMORY");
        receipts.put("benchmark:" + key, new Receipt(fingerprint, out));
        return out;
    }

    public synchronized ReadinessView readiness(Authentication auth) {
        require(auth, "internal.release.read");
        List<ReadinessCheck> checks = runtimeChecks.snapshot();
        return new ReadinessView(ReleaseGateRules.readinessStatus(checks), checks, true);
    }

    public synchronized ReleaseGateView validate(ReleaseGateValidateCommand request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key);
        String fp = String.valueOf(request);
        Object prior = receipts.get(key);
        if (prior != null) {
            Receipt receipt = (Receipt) prior;
            ReleaseGateRules.requireReplay(receipt.fingerprint,fp);
            return (ReleaseGateView) receipt.value;
        }
        if(request == null) throw new ReleaseValidationException("dryRun is required");
        List<ReleaseGate> gates=ReleaseGateRules.gates(request.releaseVersion(),request.dryRun(),request.requiredChecks());
        ReleaseGateView out = new ReleaseGateView("NOT_VERIFIED", request.releaseVersion(), true, gates); receipts.put(key, new Receipt(fp, out)); return out;
    }

    public synchronized RollbackDryRunView rollback(RollbackDryRunCommand request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key); String fingerprint = String.valueOf(request); Object prior = receipts.get(key); if (prior != null) { Receipt receipt = (Receipt) prior; ReleaseGateRules.requireReplay(receipt.fingerprint,fingerprint); return (RollbackDryRunView) receipt.value; }
        if(request == null) throw new ReleaseValidationException("releaseVersion and targetVersion are required"); ReleaseGateRules.requireRollback(request.releaseVersion(),request.targetVersion());
        RollbackDryRunView out = new RollbackDryRunView(true, request.releaseVersion(), request.targetVersion(), CURRENT); receipts.put(key, new Receipt(fingerprint, out)); return out;
    }

    public synchronized FailureDrillView drill(FailureDrillCommand request, String key, Authentication auth) {
        require(auth, "internal.release.write"); requireKey(key); String fingerprint = String.valueOf(request); Object prior = receipts.get(key); if (prior != null) { Receipt receipt = (Receipt) prior; ReleaseGateRules.requireReplay(receipt.fingerprint,fingerprint); return (FailureDrillView) receipt.value; }
        if(request == null) throw new ReleaseValidationException("Only UNAVAILABLE failure scenario is supported");
        var component = ReleaseGateRules.drillComponent(request.component(),request.scenario());
        FailureDrillView out=new FailureDrillView(component.name(),"UNAVAILABLE",component.authorizationDecision(),component.executionStatus(),component.retryAllowed(),component.retryAllowed(),component.auditEvidence());
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
    private record Receipt(String fingerprint, Object value) {}
}
