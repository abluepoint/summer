package com.abluepoint.summer.sample.agent.domain;

/** State and capability rules, independent of HTTP, tokens and storage. */
public final class AgentRules {
    private AgentRules() {}

    public static boolean permits(boolean activeMembership, boolean capabilityGranted, boolean scopeGranted) {
        return activeMembership && capabilityGranted && scopeGranted;
    }

    public static void requireVersion(long version, String expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals("\"" + version + "\"")) {
            throw new AgentPreconditionException("The resource has changed");
        }
    }

    public static String name(String value) {
        if (value == null || value.isBlank()) throw new AgentValidationException("name is required");
        return value.trim();
    }

    public static boolean isActive(String state) { return "ACTIVE".equals(state); }

    public static long lifetime(Long requested, long defaultSeconds) {
        return requested == null ? defaultSeconds : Math.max(60, Math.min(3600, requested));
    }

    public static boolean matchesRuntime(String agentId, String runtimeId, String instanceId, String workload,
                                         String assertedAgent, String assertedRuntime, String assertedInstance,
                                         String assertedWorkload, boolean verified) {
        return agentId.equals(assertedAgent) && runtimeId.equals(assertedRuntime)
                && instanceId.equals(assertedInstance) && workload.equals(assertedWorkload) && verified;
    }
}
