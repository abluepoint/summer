package com.abluepoint.summer.sample.delegation.application;


import java.time.Instant;
import java.util.List;

public record DelegationCreateCommand(String parentGrantId,String scopeType,String mcpServerId,List<String> toolPatterns,List<String> resourcePatterns,List<String> capabilityCodes,String maxRiskLevel,Boolean allowRedelegation,Integer maxDelegationDepth,Long expiresInSeconds) {}
