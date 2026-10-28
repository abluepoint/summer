package com.abluepoint.summer.sample.mcpauthorization.domain;


@org.springframework.modulith.NamedInterface("execution")
public record PublicPermitSnapshot(
        String permitId,
        String invocationId,
        String enterpriseId,
        String connectionServiceId,
        String connectionBindingId,
        String externalConnectionId,
        long connectionRevision,
        String executionContextId,
        String actorPrincipalId,
        String actorInstanceId,
        String effectiveSubjectPrincipalId,
        String toolId,
        String toolName,
        String schemaDigest,
        String argumentsDigest,
        String audience,
        String status) {}
