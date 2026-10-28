package com.abluepoint.summer.sample.authorization.application.view;

import com.abluepoint.summer.sample.authorization.domain.PolicyDefinition;

import java.time.Instant;

public record PolicyVersionView(
        String policyVersionId,
        String policyId,
        int versionNo,
        PolicyDefinition definition,
        String definitionHash,
        String publishedBy,
        Instant publishedAt,
        String status,
        boolean immutable) {}
