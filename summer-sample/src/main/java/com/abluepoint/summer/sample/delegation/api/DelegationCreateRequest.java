package com.abluepoint.summer.sample.delegation.api;
import java.time.Instant;
import java.util.List;
import com.abluepoint.summer.sample.delegation.application.DelegationCreateCommand;
public record DelegationCreateRequest(String parentGrantId,String scopeType,String mcpServerId,List<String> toolPatterns,List<String> resourcePatterns,List<String> capabilityCodes,String maxRiskLevel,Boolean allowRedelegation,Integer maxDelegationDepth,Long expiresInSeconds) {
 public DelegationCreateCommand toCommand() { return new DelegationCreateCommand(parentGrantId,scopeType,mcpServerId,toolPatterns,resourcePatterns,capabilityCodes,maxRiskLevel,allowRedelegation,maxDelegationDepth,expiresInSeconds); }
}
