package com.abluepoint.summer.sample.systemparameter.infrastructure;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static String next() {
        long timestamp = System.currentTimeMillis();
        long most = (timestamp << 16) | 0x7000L | (RANDOM.nextLong() & 0x0fffL);
        long least = (RANDOM.nextLong() & 0x3fff_ffff_ffff_ffffL) | 0x8000_0000_0000_0000L;
        return new UUID(most, least).toString();
    }
}
