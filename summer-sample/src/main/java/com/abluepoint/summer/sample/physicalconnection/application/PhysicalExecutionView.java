package com.abluepoint.summer.sample.physicalconnection.application;

public record PhysicalExecutionView(String invocationId,String permitId,String executionAttemptId,String toolName,String resultStatus,String sessionRef,long attempts,String resultDigest,String remoteRequestRef){}
