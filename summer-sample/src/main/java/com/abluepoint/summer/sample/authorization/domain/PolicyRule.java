package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;
import java.util.Map;

public record PolicyRule(
        String id,
        String effect,
        List<String> subjects,
        List<String> resources,
        List<String> actions,
        Map<String, Object> conditions,
        List<String> riskLevels,
        String riskThreshold,
        String minRisk,
        String maxRisk,
        String subject,
        String principal,
        Boolean approvalRequired,
        Integer priority) {}
