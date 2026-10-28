package com.abluepoint.summer.sample.authorization.domain;

public record PolicyEvaluationContext(
        String initiator,
        String actor,
        String effectiveSubject,
        String enterpriseId,
        String resource,
        String action,
        String risk,
        String organizationId,
        String requestId) {}
