package com.abluepoint.summer.sample.physicalconnection.api;

import com.abluepoint.summer.sample.physicalconnection.application.PhysicalInvokeCommand;
public record PhysicalInvokeRequest(String permitId,String executionAttemptId,String schemaDigest,String argumentsDigest,String sessionRef,Long timeoutMs,Integer maxRetries,String mcpAccessToken) {
 public PhysicalInvokeCommand toCommand() { return new PhysicalInvokeCommand(permitId,executionAttemptId,schemaDigest,argumentsDigest,sessionRef,timeoutMs,maxRetries,mcpAccessToken); }
}
