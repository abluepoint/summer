package com.abluepoint.summer.sample.connection;

import java.time.Instant;

public record ExecutionStatusReceipt(
        String invocationId,
        String executionAttemptId,
        String sessionRef,
        String resultStatus,
        String resultDigest,
        String remoteRequestRef,
        long latencyMs,
        Instant occurredAt) {}
