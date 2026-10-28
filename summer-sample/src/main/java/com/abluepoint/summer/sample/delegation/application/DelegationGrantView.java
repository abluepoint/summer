package com.abluepoint.summer.sample.delegation.application;


import java.time.Instant;
import java.util.List;

public record DelegationGrantView(String delegationId,String enterpriseId,String delegatorPrincipalId,String delegateAgentId,String parentGrantId,int depth,String scopeType,String mcpServerId,List<String> toolPatterns,List<String> resourcePatterns,List<String> capabilityCodes,String maxRiskLevel,boolean allowRedelegation,int maxDelegationDepth,Instant validFrom,Instant expiresAt,String status,long version) {}
