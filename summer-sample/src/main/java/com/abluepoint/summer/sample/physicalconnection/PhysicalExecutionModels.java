package com.abluepoint.summer.sample.physicalconnection;
record PhysicalInvokeRequest(String permitId,String executionAttemptId,String schemaDigest,String argumentsDigest,String sessionRef,Long timeoutMs,Integer maxRetries,String mcpAccessToken){}
record PhysicalExecutionView(String invocationId,String permitId,String executionAttemptId,String toolName,String resultStatus,String sessionRef,long attempts,String resultDigest,String remoteRequestRef){}
