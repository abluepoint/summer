package com.abluepoint.summer.sample.bff;

import java.util.Objects;

final class BffSessionService {

    private final InMemoryBffStateStore stateStore;
    private final InMemoryProductSessionStore sessionStore;
    private final OidcCodeExchanger oidcCodeExchanger;
    private final BffTokenGenerator tokenGenerator;

    BffSessionService(
            InMemoryBffStateStore stateStore,
            InMemoryProductSessionStore sessionStore,
            OidcCodeExchanger oidcCodeExchanger,
            BffTokenGenerator tokenGenerator
    ) {
        this.stateStore = Objects.requireNonNull(stateStore);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.oidcCodeExchanger = Objects.requireNonNull(oidcCodeExchanger);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
    }

    StartResult start() {
        String state = tokenGenerator.next();
        String codeVerifier = tokenGenerator.next();
        stateStore.save(state, codeVerifier);
        return new StartResult(state, codeChallenge(codeVerifier));
    }

    CallbackResult callback(String code, String state, String stateCookie) {
        String codeVerifier = stateStore.consume(state, stateCookie);
        if (code == null || code.isBlank() || codeVerifier == null) {
            return null;
        }
        OidcIdentity identity = oidcCodeExchanger.exchange(code, codeVerifier);
        String productSession = tokenGenerator.next();
        String csrfToken = tokenGenerator.next();
        sessionStore.put(productSession, identity);
        return new CallbackResult(productSession, csrfToken);
    }

    void logout(String productSession) {
        sessionStore.remove(productSession);
    }

    OidcIdentity identity(String productSession) {
        return sessionStore.find(productSession);
    }

    int sessionCount() {
        return sessionStore.size();
    }

    record CallbackResult(String productSession, String csrfToken) {
    }

    record StartResult(String state, String codeChallenge) {
    }

    private static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for OIDC PKCE", exception);
        }
    }
}
