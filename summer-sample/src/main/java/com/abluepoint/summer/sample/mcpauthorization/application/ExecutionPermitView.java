package com.abluepoint.summer.sample.mcpauthorization.application;

import java.time.Instant;
@org.springframework.modulith.NamedInterface("execution")
public record ExecutionPermitView(String permitId,String invocationId,String enterpriseId,String connectionServiceId,String connectionBindingId,String externalConnectionId,long connectionRevision,String executionContextId,String actorPrincipalId,String actorInstanceId,String effectiveSubjectPrincipalId,String toolId,String schemaDigest,String argumentsDigest,String audience,String status,Instant issuedAt,Instant expiresAt){}
