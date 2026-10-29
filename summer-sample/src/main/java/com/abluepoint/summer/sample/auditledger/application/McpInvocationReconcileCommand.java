package com.abluepoint.summer.sample.auditledger.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record McpInvocationReconcileCommand(
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs) {}
