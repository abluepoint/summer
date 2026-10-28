package com.abluepoint.summer.sample.auditledger.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditEventAppendCommand(
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

