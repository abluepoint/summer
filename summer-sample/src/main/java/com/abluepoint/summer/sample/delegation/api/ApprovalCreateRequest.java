package com.abluepoint.summer.sample.delegation.api;
import java.time.Instant;
import java.util.List;
import com.abluepoint.summer.sample.delegation.application.ApprovalCreateCommand;
public record ApprovalCreateRequest(String enterpriseId,String agentId,String agentInstanceId,String mcpServerId,String toolId,String requestDigest,String approvalScope,Long expiresInSeconds,String approverPrincipalId) {
 public ApprovalCreateCommand toCommand() { return new ApprovalCreateCommand(enterpriseId,agentId,agentInstanceId,mcpServerId,toolId,requestDigest,approvalScope,expiresInSeconds,approverPrincipalId); }
}
