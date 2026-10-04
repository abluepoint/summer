package com.abluepoint.summer.sample.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record DataScopePolicyRequest(
        String operator,
        List<DataScopeRule> rules,
        Object conditions,
        Object conditionTree,
        String sql) {
    DataScopePolicyRequest(String operator, List<DataScopeRule> rules) {
        this(operator, rules, null, null, null);
    }
}

/** A validated, structured scope rule.  The optional fields make the legacy type-only form forwards compatible. */
record DataScopeRule(
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

record DataScopePolicyView(
        String resource,
        String enterpriseId,
        String operator,
        List<DataScopeRule> rules,
        long version,
        Instant updatedAt,
        String checksum,
        Object conditions) {}

record DataScopePreviewRequest(
        String subject,
        String organizationId,
        String creatorId,
        String ownerId,
        List<String> roleAllows,
        List<String> userDenies,
        List<String> organizationIds) {
    DataScopePreviewRequest(String subject, String organizationId, String creatorId, String ownerId, List<String> roleAllows, List<String> userDenies) {
        this(subject, organizationId, creatorId, ownerId, roleAllows, userDenies, List.of());
    }
}

record DataScopePreviewView(
        String enterpriseId,
        String resource,
        String subject,
        boolean allowed,
        String decision,
        String reasonCode,
        String explanation,
        List<String> matchedConditions,
        List<String> deniedConditions,
        long policyVersion,
        String checksum,
        Map<String, Object> effectiveScope) {}

record DataScopeAuditView(
        String auditId,
        String enterpriseId,
        String resource,
        long version,
        String checksum,
        String action,
        String principal,
        String requestId,
        Instant occurredAt) {}
