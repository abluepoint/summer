package com.abluepoint.summer.sample.auditledger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record AuditEventAppendRequest(
        String eventId,
        String eventType,
        String enterpriseId,
        String requestId,
        String traceId,
        String invocationId,
        String attemptId,
        String initiator,
        String actor,
        String effectiveSubject,
        String agentInstanceId,
        Integer policyVersion,
        Long connectionRevision,
        String permitId,
        String permitJti,
        String toolId,
        String resource,
        String action,
        String decision,
        String reasonCode,
        Map<String, Object> detail) {}

record CompletionRequest(
        String permitId,
        String permitJti,
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs,
        String connectionSessionRef,
        String detail) {}

record ReconciliationRequest(
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs) {}

record AuditEventView(
        String eventId,
        String eventType,
        String enterpriseId,
        String requestId,
        String traceId,
        String invocationId,
        String attemptId,
        String initiator,
        String actor,
        String effectiveSubject,
        String agentInstanceId,
        Integer policyVersion,
        Long connectionRevision,
        String permitId,
        String permitJti,
        String toolId,
        String resource,
        String action,
        String decision,
        String reasonCode,
        Map<String, Object> detail,
        Instant occurredAt,
        String previousDigest,
        String eventDigest) {}

record AuditEventPage(
        List<AuditEventView> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}

record McpInvocationView(
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
