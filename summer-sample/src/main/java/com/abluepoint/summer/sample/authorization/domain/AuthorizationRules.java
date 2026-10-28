package com.abluepoint.summer.sample.authorization.domain;

import com.abluepoint.summer.sample.authorization.domain.exception.AuthorizationValidationException;
import com.abluepoint.summer.sample.authorization.domain.exception.AuthorizationPreconditionException;

/** Resource mutation constraints independent of protocol and persistence. */
public final class AuthorizationRules {
    private AuthorizationRules() {}
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public static void validateNameCode(String n, String c) { if (blank(n) || blank(c)) throw new AuthorizationValidationException("name and code are required"); }
    public static void requireKey(String k) { if (k == null || k.isBlank()) throw new AuthorizationValidationException("Idempotency-Key is required"); }
    public static void etag(long v, String h) { if (h == null || !h.equals("\"" + v + "\"")) throw new AuthorizationPreconditionException("The resource has changed"); }
}
