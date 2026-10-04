package com.abluepoint.summer.sample.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record DataScopePolicyRequest(String operator, List<DataScopeRule> rules) {}
record DataScopeRule(String type) {}
record DataScopePolicyView(String resource, String enterpriseId, String operator, List<DataScopeRule> rules, long version, Instant updatedAt) {}
record DataScopeCondition(String operator, List<DataScopeRule> rules) {}
