package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Fresh short reads suspend any caller RR snapshot; no cached ALLOW can bypass the version read. */
@Service
public class UnifiedAuthorizationService implements ResourceAuthorization {
    private final AuthorizationDomainVersions versions;
    private final Map<String, AuthorizationPolicy> policies;
    private final TransactionTemplate fresh;
    private final AuthorizationAuditStore audit;
    UnifiedAuthorizationService(AuthorizationDomainVersions versions, List<AuthorizationPolicy> policies,
                                PlatformTransactionManager transactions, AuthorizationAuditStore audit) {
        this.versions = versions;
        this.policies = policies.stream().collect(Collectors.toUnmodifiableMap(AuthorizationPolicy::mode, Function.identity()));
        this.audit = audit;
        fresh = new TransactionTemplate(transactions);
        fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        fresh.setReadOnly(true);
    }
    @Override public Decision decide(Request request) {
        try { return evaluate(request); }
        catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
            throw com.abluepoint.summer.common.error.SummerException.withMessage(
                    com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                    "The authoritative authorization store is unavailable");
        }
    }
    private Decision evaluate(Request request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            AuthorizationAuditStore.Evaluation result = fresh.execute(status -> {
                var before = versions.current(request.domain());
                var policy = policies.get(before.mode());
                var evaluated = audit.evaluateResource(request, before.revision(), "LEGACY".equals(before.mode()), () -> policy == null
                        ? new Decision(false, "UNSUPPORTED_AUTHORIZATION_MODE", before.revision(), request.domain(), request.resource())
                        : policy.decide(request, before.revision()));
                Decision decision = evaluated.decision();
                if (!request.domain().equals(decision.domain()) || !request.resource().equals(decision.resource())
                        || decision.revision() != before.revision()) throw new AccessDeniedException("Invalid policy result");
                return before.equals(versions.current(request.domain())) ? evaluated : null;
            });
            if (result != null) {
                if (!result.recorded()) audit.resourceDecision(request, result.decision());
                return result.decision();
            }
        }
        throw new AccessDeniedException("Authorization changed while evaluating request");
    }
}
