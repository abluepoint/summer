package com.abluepoint.summer.sample.authorization.policy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record PolicyCreateRequest(
        String name,
        String code,
        String description,
        String policyType,
        PolicyDefinition definition) {}

record PolicyDraftUpdateRequest(
        String name,
        String code,
        String description,
        String policyType,
        PolicyDefinition definition) {}

record PolicyRollbackRequest(Integer versionNo) {}

record PolicyDefinition(
        String defaultDecision,
        Boolean denyOverrides,
        String defaultEffect,
        List<PolicyRule> rules) {}

record PolicyRule(
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

record PolicyView(
        String id,
        String policyId,
        String enterpriseId,
        String name,
        String code,
        String description,
        String policyType,
        String lifecycleStatus,
        long version,
        Integer currentVersionNo,
        PolicyVersionView currentVersion,
        Integer draftVersion,
        List<PolicyVersionView> versions) {}

record PolicyDetailView(
        PolicyView policy,
        List<PolicyVersionView> versions,
        List<Map<String, Object>> bindings,
        List<PolicyTestCaseView> testCases) {}

record PolicyVersionView(
        String policyVersionId,
        String policyId,
        int versionNo,
        PolicyDefinition definition,
        String definitionHash,
        String publishedBy,
        Instant publishedAt,
        String status,
        boolean immutable) {}

record PolicyValidationView(String policyId, boolean valid, List<String> errors, List<String> warnings) {}

record PolicyEvaluationRequest(
        String initiator,
        String actor,
        String effectiveSubject,
        String enterpriseId,
        String resource,
        String action,
        String risk,
        String organizationId,
        String requestId) {}

record PolicyEvaluationIdentity(String initiator, String actor, String effectiveSubject, String enterpriseId) {}

record PolicyEvaluationView(
        String decision,
        boolean allow,
        List<String> matchedRules,
        boolean approvalRequired,
        Integer policyVersion,
        String reasonCode,
        PolicyEvaluationIdentity identity,
        String requestId) {}

record PolicyTestCaseCreateRequest(
        String name,
        Map<String, Object> input,
        String expectedDecision) {}

record PolicyTestCaseView(
        String testCaseId,
        String policyId,
        String name,
        Map<String, Object> input,
        String expectedDecision,
        String lastActualDecision,
        Instant lastRunAt) {}
