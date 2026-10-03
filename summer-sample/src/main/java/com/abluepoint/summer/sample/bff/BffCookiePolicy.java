package com.abluepoint.summer.sample.bff;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

final class BffCookiePolicy {

    static final String OIDC_STATE_COOKIE = "aict-oidc-state";
    static final String PRODUCT_SESSION_COOKIE = "aict-product-session";
    static final String CSRF_COOKIE = "aict-csrf-token";

    private final String cookiePath;

    BffCookiePolicy(String authorizeBaseUrl, String cookiePath) {
        if (authorizeBaseUrl == null || authorizeBaseUrl.isBlank()) {
            throw new IllegalArgumentException("authorizeBaseUrl must not be blank");
        }
        if (cookiePath == null || cookiePath.isBlank() || !cookiePath.startsWith("/")) {
            throw new IllegalArgumentException("cookiePath must be an absolute path");
        }
        this.authorizeBaseUrl = authorizeBaseUrl;
        this.cookiePath = cookiePath;
    }

    private final String authorizeBaseUrl;

    String authorizationUri(String state, String codeChallenge) {
        return authorizeBaseUrl + "oauth2/authorize?response_type=code&client_id=aict-identity&scope=openid%20profile&state="
                + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8)
                + "&code_challenge="
                + java.net.URLEncoder.encode(codeChallenge, java.nio.charset.StandardCharsets.UTF_8)
                + "&code_challenge_method=S256";
    }

    String stateCookie(String state) {
        return cookie(OIDC_STATE_COOKIE, state, true, Duration.ofMinutes(5));
    }

    String productSessionCookie(String value) {
        return cookie(PRODUCT_SESSION_COOKIE, value, true, Duration.ofHours(8));
    }

    String csrfCookie(String value) {
        return cookie(CSRF_COOKIE, value, false, Duration.ofHours(8));
    }

    String clearCookie(String name, boolean httpOnly) {
        return cookie(name, "", httpOnly, Duration.ZERO);
    }

    private String cookie(String name, String value, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("Lax")
                .path(cookiePath)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
