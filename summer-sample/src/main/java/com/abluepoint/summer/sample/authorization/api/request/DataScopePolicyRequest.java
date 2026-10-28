package com.abluepoint.summer.sample.authorization.api.request;

import com.abluepoint.summer.sample.authorization.application.command.DataScopePolicyCommand;

import com.abluepoint.summer.sample.authorization.domain.DataScopeRule;

import java.util.List;

public record DataScopePolicyRequest(
        String operator,
        List<DataScopeRule> rules,
        Object conditions,
        Object conditionTree,
        String sql) {
    public DataScopePolicyRequest(String operator, List<DataScopeRule> rules) {
        this(operator, rules, null, null, null);
    }

    public com.abluepoint.summer.sample.authorization.application.command.DataScopePolicyCommand toCommand() {
        return new com.abluepoint.summer.sample.authorization.application.command.DataScopePolicyCommand(operator, rules, conditions, conditionTree, sql);
    }
}
