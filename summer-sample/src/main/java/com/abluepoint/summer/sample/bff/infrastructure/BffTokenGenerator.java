package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.domain.BffTokenSource;

import java.security.SecureRandom;
import java.util.Base64;

public final class BffTokenGenerator implements BffTokenSource {

    private final SecureRandom random = new SecureRandom();

    public String next() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
