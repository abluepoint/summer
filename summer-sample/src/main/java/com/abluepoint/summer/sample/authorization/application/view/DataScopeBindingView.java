package com.abluepoint.summer.sample.authorization.application.view;

import com.abluepoint.summer.sample.authorization.domain.DataScopeRule;

import java.util.List;

public record DataScopeBindingView(String subjectType, String subjectId, String effect, String operator,
                            List<DataScopeRule> rules, Object conditions, long version, String checksum) {}
