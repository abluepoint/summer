package com.abluepoint.summer.sample.auditledger.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.auditledger.application.McpInvocationCompleteCommand;
public record McpInvocationCompleteRequest(
        String permitId,
        String permitJti,
        String executionAttemptId,
        String resultStatus,
        String resultDigest,
        String remoteRequestId,
        Long latencyMs,
        String connectionSessionRef,
        String detail) { public McpInvocationCompleteCommand toCommand() { return new McpInvocationCompleteCommand(permitId,permitJti,executionAttemptId,resultStatus,resultDigest,remoteRequestId,latencyMs,connectionSessionRef,detail); } }
