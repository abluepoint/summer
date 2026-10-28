package com.abluepoint.summer.sample.authorization.application.command;

import com.abluepoint.summer.sample.authorization.domain.PolicyDefinition;

public record PolicyDraftUpdateCommand(
        String name,
        String code,
        String description,
        String policyType,
        PolicyDefinition definition) {}
