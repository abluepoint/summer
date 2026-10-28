package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;

/** A validated, structured scope rule.  The optional fields make the legacy type-only form forwards compatible. */
public record DataScopeRule(
        String type,
        String field,
        String operator,
        Object value,
        String effect,
        List<DataScopeRule> children) {
    DataScopeRule(String type) {
        this(type, null, null, null, null, List.of());
    }
}
