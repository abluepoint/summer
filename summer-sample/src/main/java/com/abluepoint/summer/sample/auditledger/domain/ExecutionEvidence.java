package com.abluepoint.summer.sample.auditledger.domain;


@org.springframework.modulith.NamedInterface("ledger")
public record ExecutionEvidence(
        String enterpriseId,
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
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs,
        String connectionSessionRef) {}
