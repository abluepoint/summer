package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;

public record PolicyDefinition(
        String defaultDecision,
        Boolean denyOverrides,
        String defaultEffect,
        List<PolicyRule> rules) {}
