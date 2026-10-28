package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationContext;

public record PolicyEvaluationRequest(
        String initiator,
        String actor,
        String effectiveSubject,
        String enterpriseId,
        String resource,
        String action,
        String risk,
        String organizationId,
        String requestId) {    public com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationContext toContext() {
        return new com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationContext(initiator, actor, effectiveSubject, enterpriseId, resource, action, risk, organizationId, requestId);
    }
}
