package com.abluepoint.summer.sample.mcpauthorization.application;

import java.time.Instant;
public record ExecutionDecisionCommand(String enterpriseId,String connectionServiceId,String connectionBindingId,String externalConnectionId,Long connectionRevision,String executionContextId,String actorPrincipalId,String actorInstanceId,String effectiveSubjectPrincipalId,String toolId,String toolName,String schemaDigest,String argumentsDigest,String audience,String risk,Boolean allow,String approvalId){}
