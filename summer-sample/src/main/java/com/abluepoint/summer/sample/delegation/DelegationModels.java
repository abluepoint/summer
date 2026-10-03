package com.abluepoint.summer.sample.delegation;

import java.time.Instant;
import java.util.List;

record DelegationCreateRequest(String parentGrantId,String scopeType,String mcpServerId,List<String> toolPatterns,List<String> resourcePatterns,List<String> capabilityCodes,String maxRiskLevel,Boolean allowRedelegation,Integer maxDelegationDepth,Long expiresInSeconds) {}
record DelegationGrantView(String delegationId,String enterpriseId,String delegatorPrincipalId,String delegateAgentId,String parentGrantId,int depth,String scopeType,String mcpServerId,List<String> toolPatterns,List<String> resourcePatterns,List<String> capabilityCodes,String maxRiskLevel,boolean allowRedelegation,int maxDelegationDepth,Instant validFrom,Instant expiresAt,String status,long version) {}
record ApprovalCreateRequest(String enterpriseId,String agentId,String agentInstanceId,String mcpServerId,String toolId,String requestDigest,String approvalScope,Long expiresInSeconds,String approverPrincipalId) {}
record ApprovalView(String approvalId,String enterpriseId,String requestedByPrincipalId,String agentId,String agentInstanceId,String approverPrincipalId,String mcpServerId,String toolId,String requestDigest,String approvalScope,String status,Instant expiresAt,Instant approvedAt,Instant consumedAt,String approvedBy,long version) {}
record DelegationAuditView(String action,String delegationId,String approvalId,String enterpriseId,String principal,Instant occurredAt) {}
