package com.abluepoint.summer.sample.delegation.application;


import java.time.Instant;
import java.util.List;

public record ApprovalView(String approvalId,String enterpriseId,String requestedByPrincipalId,String agentId,String agentInstanceId,String approverPrincipalId,String mcpServerId,String toolId,String requestDigest,String approvalScope,String status,Instant expiresAt,Instant approvedAt,Instant consumedAt,String approvedBy,long version) {}
