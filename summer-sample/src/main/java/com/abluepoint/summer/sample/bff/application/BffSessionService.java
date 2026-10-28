package com.abluepoint.summer.sample.bff.application;

import com.abluepoint.summer.sample.bff.domain.BffStateStore;
import com.abluepoint.summer.sample.bff.domain.BffTokenSource;
import com.abluepoint.summer.sample.bff.domain.ProductSessionStore;

import com.abluepoint.summer.sample.bff.domain.OidcCodeExchanger;
import com.abluepoint.summer.sample.bff.domain.OidcIdentity;

import java.util.Objects;

public final class BffSessionService {

    private final BffStateStore stateStore;
    private final ProductSessionStore sessionStore;
    private final OidcCodeExchanger oidcCodeExchanger;
    private final BffTokenSource tokenGenerator;

    public BffSessionService(
            BffStateStore stateStore,
            ProductSessionStore sessionStore,
            OidcCodeExchanger oidcCodeExchanger,
            BffTokenSource tokenGenerator
    ) {
        this.stateStore = Objects.requireNonNull(stateStore);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.oidcCodeExchanger = Objects.requireNonNull(oidcCodeExchanger);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
    }

    public StartResult start() {
        String state = tokenGenerator.next();
        String codeVerifier = tokenGenerator.next();
        stateStore.save(state, codeVerifier);
        return new StartResult(state, codeChallenge(codeVerifier));
    }

    public CallbackResult callback(String code, String state, String stateCookie) {
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

    public void logout(String productSession) {
        sessionStore.remove(productSession);
    }

    public OidcIdentity identity(String productSession) {
        return sessionStore.find(productSession);
    }

    public int sessionCount() {
        return sessionStore.size();
    }

    public record CallbackResult(String productSession, String csrfToken) {
    }

    public record StartResult(String state, String codeChallenge) {
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
