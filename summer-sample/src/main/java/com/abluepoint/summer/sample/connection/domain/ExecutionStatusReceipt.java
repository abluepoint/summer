package com.abluepoint.summer.sample.connection.domain;


import java.time.Instant;

@org.springframework.modulith.NamedInterface("execution")
public record ExecutionStatusReceipt(
        String invocationId,
        String executionAttemptId,
        String sessionRef,
        String resultStatus,
        String resultDigest,
        String remoteRequestRef,
        long latencyMs,
        Instant occurredAt) {}
