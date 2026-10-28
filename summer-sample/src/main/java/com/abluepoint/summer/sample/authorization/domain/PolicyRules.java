package com.abluepoint.summer.sample.authorization.domain;

import java.util.*;
import com.abluepoint.summer.sample.authorization.domain.exception.PolicyValidationException;

/** Pure policy validation and matching rules shared by publication and evaluation. */
public final class PolicyRules {
    private PolicyRules() {}
    private static final Set<String> CONDITION_WHITELIST = Set.of(
            "organizationId", "risk", "riskLevel", "riskLevels", "subject", "actor",
            "initiator", "effectiveSubject", "agentId", "applicationId", "scope");
    private static final List<String> RISK_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public static boolean matches(PolicyRule rule, PolicyEvaluationContext request) {
        if (rule == null) return false;
        if (!matchAny(rule.subjects(), request.effectiveSubject(), request.actor(), request.initiator())
                && !matchAny(rule.subject() == null ? List.of() : List.of(rule.subject()), request.effectiveSubject(), request.actor(), request.initiator())
                && !matchAny(rule.principal() == null ? List.of() : List.of(rule.principal()), request.effectiveSubject(), request.actor(), request.initiator())) return false;
        if (!matchPattern(rule.resources(), request.resource()) || !matchPattern(rule.actions(), request.action())) return false;
        if (rule.riskLevels() != null && !rule.riskLevels().isEmpty() && !rule.riskLevels().stream().anyMatch(x -> x.equalsIgnoreCase(request.risk()))) return false;
        if (rule.minRisk() != null && risk(request.risk()) < risk(rule.minRisk())) return false;
        if (rule.maxRisk() != null && risk(request.risk()) > risk(rule.maxRisk())) return false;
        if (rule.riskThreshold() != null && risk(request.risk()) < risk(rule.riskThreshold())) return false;
        if (rule.conditions() != null) {
            for (Map.Entry<String, Object> entry : rule.conditions().entrySet()) {
                String actual = conditionValue(entry.getKey(), request);
                // A condition is only evaluated when that context attribute is
                // present.  The caller may intentionally omit optional context
                // (for example an organization) and the rule remains eligible.
                if (actual != null && !Objects.equals(String.valueOf(entry.getValue()), actual)) return false;
            }
        }
        return true;
    }

    public static String conditionValue(String key, PolicyEvaluationContext request) {
        return switch (key) {
            case "organizationId" -> request.organizationId();
            case "risk", "riskLevel" -> request.risk();
            case "subject", "effectiveSubject" -> request.effectiveSubject();
            case "actor" -> request.actor();
            case "initiator" -> request.initiator();
            case "scope", "agentId", "applicationId" -> null;
            default -> null;
        };
    }

    public static boolean matchAny(List<String> values, String... subjects) {
        if (values == null || values.isEmpty()) return true;
        return values.stream().anyMatch(value -> "*".equals(value) || Arrays.stream(subjects).anyMatch(s -> s != null && value.equalsIgnoreCase(s)));
    }

    public static boolean matchPattern(List<String> patterns, String value) {
        if (patterns == null || patterns.isEmpty()) return true;
        if (value == null) return false;
        return patterns.stream().anyMatch(pattern -> "*".equals(pattern) ||
                (pattern.endsWith("*") && value.startsWith(pattern.substring(0, pattern.length() - 1))) || pattern.equals(value));
    }

    public static int priority(PolicyRule rule) { return rule.priority() == null ? 0 : rule.priority(); }
    public static String effect(PolicyRule rule) { return rule.effect() == null ? "DENY" : rule.effect(); }
    public static String ruleId(PolicyRule rule) { return rule.id(); }
    public static int risk(String value) { if (value == null) return 0; int index = RISK_ORDER.indexOf(value.toUpperCase(Locale.ROOT)); return index < 0 ? 0 : index; }

    public static void validateDefinition(PolicyDefinition definition) {
        validateDefinition(definition, true);
    }

    public static void validateDefinition(PolicyDefinition definition, boolean strictConditions) {
        if (definition == null) throw new PolicyValidationException("definition is required");
        if (definition.defaultDecision() != null && !Set.of("ALLOW", "DENY").contains(definition.defaultDecision().toUpperCase(Locale.ROOT))) {
            throw new PolicyValidationException("defaultDecision must be ALLOW or DENY");
        }
        if (definition.rules() == null) return;
        for (PolicyRule rule : definition.rules()) {
            if (rule == null) throw new PolicyValidationException("rule is required");
            if (strictConditions && rule.conditions() != null) {
                for (String condition : rule.conditions().keySet()) {
                    if (!CONDITION_WHITELIST.contains(condition)) throw new PolicyValidationException("Unsupported condition: " + condition);
                }
            }
            if (rule.effect() != null && !Set.of("ALLOW", "DENY").contains(rule.effect().toUpperCase(Locale.ROOT))) {
                throw new PolicyValidationException("Invalid rule effect");
            }
        }
    }

    public static PolicyDefinition normalize(PolicyDefinition input) {
        if (input == null) return new PolicyDefinition("DENY", true, "DENY", List.of());
        return new PolicyDefinition(input.defaultDecision() == null ? "DENY" : input.defaultDecision().toUpperCase(Locale.ROOT),
                input.denyOverrides() == null || input.denyOverrides(), input.defaultEffect() == null ? "DENY" : input.defaultEffect().toUpperCase(Locale.ROOT),
                input.rules() == null ? List.of() : List.copyOf(input.rules()));
    }

}
