package com.abluepoint.summer.sample.mcpauthorization.domain;


/** Exact invocation identity used to bind a high-risk approval. */
@org.springframework.modulith.NamedInterface("approval")
public record HumanApprovalBinding(
        String enterpriseId,
        String connectionServiceId,
        String connectionBindingId,
        String externalConnectionId,
        String executionContextId,
        String initiatorPrincipalId,
        String actorPrincipalId,
        String actorInstanceId,
        String effectiveSubjectPrincipalId,
        String toolId,
        String toolName,
        String schemaDigest,
        String argumentsDigest,
        String audience) {}
