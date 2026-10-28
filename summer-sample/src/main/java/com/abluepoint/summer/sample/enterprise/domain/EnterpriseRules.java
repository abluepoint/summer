package com.abluepoint.summer.sample.enterprise.domain;

public final class EnterpriseRules {
    private EnterpriseRules() {}
    public static String review(String current, String decision, String reason) {
        if (("REJECT".equals(decision) || "REQUEST_INFO".equals(decision)) && (reason == null || reason.isBlank()))
            throw new ReviewValidationException("reason is required");
        String next = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "REQUEST_INFO" -> "NEEDS_INFO";
            default -> throw new ReviewValidationException("unsupported decision");
        };
        if (!"PENDING".equals(current) && !"NEEDS_INFO".equals(current)) throw new ReviewValidationException("illegal status transition");
        return next;
    }
    public static String transition(String action) {
        return switch (action) {
            case "freeze" -> "FROZEN";
            case "disable" -> "DISABLED";
            case "reject" -> "REJECTED";
            default -> throw new PlatformEnterpriseValidationException("unsupported enterprise action");
        };
    }
    public static String revision(String enterpriseId, long version) { return enterpriseId + ":v" + version; }
    public static boolean matchesVersion(String revision, String expected) { return ("\"" + revision + "\"").equals(expected); }
    public static void validateCreation(String name, String code) {
        if (name == null || name.isBlank() || name.length() > 200 || code == null || !code.matches("[a-z][a-z0-9-]{2,63}"))
            throw new IllegalArgumentException("name and code are required; code must be lowercase kebab-case");
    }
    public static void validateCreator(String creatorSubject) {
        if (creatorSubject == null || creatorSubject.isBlank() || creatorSubject.length() > 128)
            throw new IllegalArgumentException("A valid creator subject is required");
    }
}
