package com.abluepoint.summer.sample.mcpauthorization.api;
import java.time.Instant;
import com.abluepoint.summer.sample.mcpauthorization.application.ExecutionDecisionCommand;
public record ExecutionDecisionRequest(String enterpriseId,String connectionServiceId,String connectionBindingId,String externalConnectionId,Long connectionRevision,String executionContextId,String actorPrincipalId,String actorInstanceId,String effectiveSubjectPrincipalId,String toolId,String toolName,String schemaDigest,String argumentsDigest,String audience,String risk,Boolean allow,String approvalId) {
 public ExecutionDecisionCommand toCommand() { return new ExecutionDecisionCommand(enterpriseId,connectionServiceId,connectionBindingId,externalConnectionId,connectionRevision,executionContextId,actorPrincipalId,actorInstanceId,effectiveSubjectPrincipalId,toolId,toolName,schemaDigest,argumentsDigest,audience,risk,allow,approvalId); }
}
