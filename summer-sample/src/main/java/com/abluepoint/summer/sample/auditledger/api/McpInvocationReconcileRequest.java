package com.abluepoint.summer.sample.auditledger.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.auditledger.application.McpInvocationReconcileCommand;
public record McpInvocationReconcileRequest(
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs) { public McpInvocationReconcileCommand toCommand() { return new McpInvocationReconcileCommand(executionAttemptId,resultStatus,resultDigest,remoteRequestId,latencyMs); } }
