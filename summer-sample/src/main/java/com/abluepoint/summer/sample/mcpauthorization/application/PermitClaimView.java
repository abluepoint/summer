package com.abluepoint.summer.sample.mcpauthorization.application;

import java.time.Instant;
@org.springframework.modulith.NamedInterface("execution")
public record PermitClaimView(String permitId,String invocationId,String executionAttemptId,String status,Instant claimedAt){}
