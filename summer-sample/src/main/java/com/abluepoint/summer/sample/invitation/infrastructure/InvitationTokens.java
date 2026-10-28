package com.abluepoint.summer.sample.invitation.infrastructure;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public final class InvitationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private InvitationTokens() {}
    public static String token() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    public static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public static String shortHash(String value) { return hash(value).substring(0, 16); }
}
