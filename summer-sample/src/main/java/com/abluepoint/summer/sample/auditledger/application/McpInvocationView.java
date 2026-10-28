package com.abluepoint.summer.sample.auditledger.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record McpInvocationView(
        String invocationId,
        String enterpriseId,
        String requestId,
        String traceId,
        String initiator,
        String actor,
        String effectiveSubject,
        String agentInstanceId,
        String toolId,
        String resource,
        String action,
        String decision,
        String reasonCode,
        Integer policyVersion,
        String permitId,
        String permitJti,
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs,
        boolean revoked,
        List<AuditEventView> events) {}
