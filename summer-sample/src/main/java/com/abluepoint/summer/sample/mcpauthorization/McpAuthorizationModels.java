package com.abluepoint.summer.sample.mcpauthorization;
import java.time.Instant;
record ExecutionDecisionRequest(String enterpriseId,String connectionServiceId,String connectionBindingId,String externalConnectionId,Long connectionRevision,String executionContextId,String actorPrincipalId,String actorInstanceId,String effectiveSubjectPrincipalId,String toolId,String toolName,String schemaDigest,String argumentsDigest,String audience,String risk,Boolean allow,String approvalId){}
record ExecutionPermitView(String permitId,String invocationId,String enterpriseId,String connectionServiceId,String connectionBindingId,String externalConnectionId,long connectionRevision,String executionContextId,String actorPrincipalId,String actorInstanceId,String effectiveSubjectPrincipalId,String toolId,String schemaDigest,String argumentsDigest,String audience,String status,Instant issuedAt,Instant expiresAt){}
record ExecutionDecisionView(String invocationId,String decision,boolean allow,String reasonCode,String approvalId,ExecutionPermitView permit){}
record PermitClaimRequest(String executionAttemptId){}
record PermitClaimView(String permitId,String invocationId,String executionAttemptId,String status,Instant claimedAt){}
