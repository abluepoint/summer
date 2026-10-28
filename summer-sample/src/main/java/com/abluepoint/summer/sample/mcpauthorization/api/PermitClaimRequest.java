package com.abluepoint.summer.sample.mcpauthorization.api;
import java.time.Instant;
import com.abluepoint.summer.sample.mcpauthorization.application.PermitClaimCommand;
public record PermitClaimRequest(String executionAttemptId) {
 public PermitClaimCommand toCommand() { return new PermitClaimCommand(executionAttemptId); }
}
