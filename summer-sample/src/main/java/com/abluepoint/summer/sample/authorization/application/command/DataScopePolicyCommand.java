package com.abluepoint.summer.sample.authorization.application.command;

import com.abluepoint.summer.sample.authorization.domain.DataScopeRule;

import java.util.List;

public record DataScopePolicyCommand(
        String operator,
        List<DataScopeRule> rules,
        Object conditions,
        Object conditionTree,
        String sql) {
    public DataScopePolicyCommand(String operator, List<DataScopeRule> rules) {
        this(operator, rules, null, null, null);
    }
}
