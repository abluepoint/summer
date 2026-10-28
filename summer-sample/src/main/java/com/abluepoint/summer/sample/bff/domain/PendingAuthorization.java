package com.abluepoint.summer.sample.bff.domain;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** One-use authorization state must match the browser binding before consumption. */
public record PendingAuthorization(String codeVerifier, Instant expiresAt) {
    public boolean expiredAt(Instant now) { return !expiresAt.isAfter(now); }
    public static boolean matches(String state, String cookieState) {
        return state != null && cookieState != null && MessageDigest.isEqual(
                state.getBytes(StandardCharsets.UTF_8), cookieState.getBytes(StandardCharsets.UTF_8));
    }
}
