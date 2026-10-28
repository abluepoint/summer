package com.abluepoint.summer.sample.mcpauthorization.application;

import java.time.Instant;
public record ExecutionDecisionView(String invocationId,String decision,boolean allow,String reasonCode,String approvalId,ExecutionPermitView permit){}
