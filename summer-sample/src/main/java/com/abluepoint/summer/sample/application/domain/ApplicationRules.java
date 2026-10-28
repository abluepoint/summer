package com.abluepoint.summer.sample.application.domain;

/** State and capability rules, independent of HTTP, tokens and storage. */
public final class ApplicationRules {
    private ApplicationRules() {}

    public static boolean permits(boolean activeMembership, boolean capabilityGranted, boolean scopeGranted) {
        return activeMembership && capabilityGranted && scopeGranted;
    }

    public static void requireVersion(long version, String expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals("\"" + version + "\"")) {
            throw new ApplicationPreconditionException("The resource has changed");
        }
    }

    public static String name(String value) {
        if (value == null || value.isBlank()) throw new ApplicationValidationException("name is required");
        return value.trim();
    }

    public static String rotatedCredentialStatus(String currentStatus) {
        return "ACTIVE".equals(currentStatus) ? "GRACE" : currentStatus;
    }
}
