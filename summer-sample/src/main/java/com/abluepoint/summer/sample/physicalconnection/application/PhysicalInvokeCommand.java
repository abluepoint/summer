package com.abluepoint.summer.sample.physicalconnection.application;

public record PhysicalInvokeCommand(String permitId,String executionAttemptId,String schemaDigest,String argumentsDigest,String sessionRef,Long timeoutMs,Integer maxRetries,String mcpAccessToken){}
