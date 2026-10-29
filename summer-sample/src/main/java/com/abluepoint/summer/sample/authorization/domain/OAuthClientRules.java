package com.abluepoint.summer.sample.authorization.domain;

import java.net.URI;
import java.util.List;

/** Public-client trust and lifecycle constraints independent of HTTP and storage. */
public final class OAuthClientRules {
    public static final String PUBLIC_AUTHENTICATION_METHOD = "none";
    public static final String PUBLIC_GRANT_TYPE = "authorization_code";
    public static final boolean REQUIRE_PROOF_KEY = true;
    public static final boolean REQUIRE_AUTHORIZATION_CONSENT = false;
    private OAuthClientRules() {}

    public static void requireCreation(String clientId, String clientName, Boolean publicClient,
                                       List<String> redirectUris, List<String> scopes) {
        if (blank(clientId) || blank(clientName) || redirectUris == null || redirectUris.isEmpty()
                || scopes == null || scopes.isEmpty())
            throw new IllegalArgumentException("clientId, clientName, redirectUris and scopes are required");
        if (!Boolean.TRUE.equals(publicClient))
            throw new IllegalArgumentException("only public PKCE clients are supported by this endpoint");
    }

    public static void requireUpdate(String clientId, int version, String status, String clientName,
                                     List<String> redirectUris, List<String> scopes) {
        if (blank(clientId) || version < 1) throw new IllegalArgumentException("version is required");
        if (status == null && clientName == null && redirectUris == null && scopes == null)
            throw new IllegalArgumentException("at least one client field is required");
    }

    public static void requireVersion(int current, int expected) {
        if (current != expected) throw new OAuthClientVersionConflictException();
    }

    public static void requireStatus(String status) {
        if (!java.util.Set.of("ACTIVE", "DISABLED").contains(status))
            throw new IllegalArgumentException("status must be ACTIVE or DISABLED");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public static String clientId(String value) {
        if (blank(value) || value.trim().length() > 100 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("clientId must contain 1 to 100 characters without control characters");
        return value.trim();
    }

    public static String clientName(String value) {
        if (blank(value) || value.trim().length() > 200 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("clientName must contain 1 to 200 characters without control characters");
        return value.trim();
    }

    private static List<String> values(List<String> value, String field, int maxLength) {
        if (value.isEmpty() || value.stream().anyMatch(v -> v == null || v.isBlank()))
            throw new IllegalArgumentException(field + " must contain non-empty values");
        List<String> result = value.stream().map(String::trim).distinct().toList();
        if (String.join(",", result).length() > maxLength)
            throw new IllegalArgumentException(field + " exceeds the storage limit");
        return result;
    }

    public static List<String> redirectUris(List<String> values) {
        List<String> result = values(values, "redirectUris", 2000);
        for (String value : result) {
            URI uri;
            try { uri = URI.create(value); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("redirectUris must contain valid absolute URIs"); }
            boolean loopback = java.util.Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(String.valueOf(uri.getHost()));
            if (uri.getHost() == null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                    || value.contains("*") || value.contains(",")
                    || !("https".equalsIgnoreCase(uri.getScheme()) || (loopback && "http".equalsIgnoreCase(uri.getScheme()))))
                throw new IllegalArgumentException("redirectUris require HTTPS (HTTP is allowed only for loopback), without fragments, user info, wildcards or commas");
        }
        return result;
    }

    public static List<String> scopes(List<String> values) {
        List<String> result = values(values, "scopes", 1000);
        if (result.stream().anyMatch(v -> v.chars().anyMatch(c -> c < 0x21 || c > 0x7e || c == '"' || c == 0x5c || c == ',')))
            throw new IllegalArgumentException("scopes must be individual OAuth scope tokens without commas");
        return result;
    }

}
