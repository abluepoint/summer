package com.abluepoint.summer.sample.bff;

import java.security.SecureRandom;
import java.util.Base64;

final class BffTokenGenerator {

    private final SecureRandom random = new SecureRandom();

    String next() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
