package com.abluepoint.summer.sample.delegation.application;


import java.time.Instant;
import java.util.List;

public record ApprovalCreateCommand(String enterpriseId,String agentId,String agentInstanceId,String mcpServerId,String toolId,String requestDigest,String approvalScope,Long expiresInSeconds,String approverPrincipalId) {}
