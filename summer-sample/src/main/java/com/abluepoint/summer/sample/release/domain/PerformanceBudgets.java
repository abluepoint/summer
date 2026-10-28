package com.abluepoint.summer.sample.release.domain;

import java.util.List;
public record PerformanceBudgets(
        int managementListP95Ms,
        int cachedAuthorizationP95Ms,
        int mcpDecisionP95Ms,
        int authorizationDecisionP95Ms,
        int permitClaimP95Ms,
        int auditWriteP95Ms) {}
