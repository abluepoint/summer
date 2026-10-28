package com.abluepoint.summer.sample.authorization.application.view;

import com.abluepoint.summer.sample.authorization.domain.DataScopeRule;

import java.time.Instant;
import java.util.List;

public record DataScopePolicyView(
        String resource,
        String enterpriseId,
        String operator,
        List<DataScopeRule> rules,
        long version,
        Instant updatedAt,
        String checksum,
        Object conditions) {}
