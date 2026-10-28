package com.abluepoint.summer.sample.authorization.application.view;

import com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationIdentity;

import java.util.List;

public record PolicyEvaluationView(
        String decision,
        boolean allow,
        List<String> matchedRules,
        boolean approvalRequired,
        Integer policyVersion,
        String reasonCode,
        PolicyEvaluationIdentity identity,
        String requestId) {}
