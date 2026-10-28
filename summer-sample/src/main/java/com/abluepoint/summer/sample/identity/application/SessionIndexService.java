package com.abluepoint.summer.sample.identity.application;

import com.abluepoint.summer.sample.identity.domain.SessionIndexStore;

import com.abluepoint.summer.sample.identity.application.view.SessionAuthenticationSnapshot;
import com.abluepoint.summer.sample.identity.application.view.SessionRevokeView;
import com.abluepoint.summer.sample.identity.application.view.SessionIndexPageView;
import com.abluepoint.summer.sample.identity.application.view.SessionIndexView;
import com.abluepoint.summer.sample.identity.domain.SessionNotFoundException;
import com.abluepoint.summer.sample.identity.domain.SessionRecord;
import com.abluepoint.summer.sample.identity.domain.SessionReferenceInvalidException;
import com.abluepoint.summer.sample.identity.domain.SessionRevokedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Session-management use cases; persistence and bounded transport failures belong to the store adapter. */
@Service
@ConditionalOnProperty(prefix = "summer.sample.session-index", name = "enabled", havingValue = "true")
public final class SessionIndexService {
    private static final String AVAILABLE = "AVAILABLE";
    private final SessionIndexStore store;

    public SessionIndexService(SessionIndexStore store) { this.store = store; }

    public SessionIndexView ensureCurrent(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null || authentication == null || !authentication.isAuthenticated()) return null;
        String reference = reference(session.getId());
        Instant now = Instant.now();
        int interval = session.getMaxInactiveInterval();
        Instant expiresAt = now.plusSeconds(interval > 0 ? interval : 1_800);
        SessionAuthenticationSnapshot auth = authenticationSnapshot(authentication);
        return store.execute(() -> {
            SessionRecord old = store.read(reference);
            SessionRecord record = new SessionRecord(reference, authentication.getName(), clientId(authentication),
                    old != null && "REVOKED".equals(old.status()) ? "REVOKED" : "ACTIVE",
                    old == null ? now : old.createdAt(), now, expiresAt, auth.claims(), auth.authorities());
            if ("REVOKED".equals(record.status())) throw new SessionRevokedException();
            if (!store.writeActive(record, ttl(expiresAt, now), true)) throw new SessionRevokedException();
            return view(record, true);
        });
    }

    public SessionIndexPageView list(String principal, Authentication authentication, HttpServletRequest request) {
        requireRead(authentication);
        String requested = principal == null || principal.isBlank() ? authentication.getName() : principal.trim();
        if (!requested.equals(authentication.getName())) requireAdmin(authentication);
        String current = currentReference(request);
        return store.execute(() -> {
            Set<String> references = store.references(requested);
            if (references == null || references.isEmpty()) return new SessionIndexPageView(AVAILABLE, List.of());
            List<SessionIndexView> values = new ArrayList<>();
            for (String reference : references) {
                SessionRecord record = store.read(reference);
                if (record == null || expired(record)) {
                    store.removeReference(requested, reference);
                    continue;
                }
                values.add(view(record, Objects.equals(current, reference)));
            }
            values.sort(Comparator.comparing(SessionIndexView::lastSeenAt).reversed());
            return new SessionIndexPageView(AVAILABLE, values);
        });
    }

    public SessionRevokeView revoke(String reference, Authentication authentication, HttpServletRequest request) {
        validateReference(reference);
        requireWrite(authentication);
        return store.execute(() -> {
            SessionRecord prior = store.read(reference);
            if (prior == null || expired(prior)) throw new SessionNotFoundException();
            if (!prior.principal().equals(authentication.getName()) && !has(authentication, "ROLE_ADMIN")) {
                throw new AccessDeniedException("Session belongs to another principal");
            }
            SessionRecord revoked = prior.revokeAt(Instant.now());
            store.writeRevoked(revoked);
            return new SessionRevokeView(reference, "REVOKED", "UNCHANGED");
        });
    }

    public void revokeCurrent(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        String ref = reference(session.getId());
        store.execute(() -> {
            SessionRecord prior = store.read(ref);
            if (prior != null) {
                store.writeRevoked(prior.revokeAt(Instant.now()));
            }
            return null;
        });
    }

    public SessionRecord load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        return store.execute(() -> store.read(reference(sessionId)));
    }

    public void assertActive(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        SessionRecord record = load(session.getId());
        if (record != null && "REVOKED".equals(record.status())) throw new SessionRevokedException();
        if (record != null && expired(record)) throw new SessionRevokedException();
    }

    public SessionAuthenticationSnapshot authenticationSnapshot(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new SessionAuthenticationSnapshot(authentication.getName(), jwt.getToken().getClaims(),
                    authorities(authentication), jwt.getToken().getIssuedAt(), jwt.getToken().getExpiresAt());
        }
        return new SessionAuthenticationSnapshot(authentication.getName(), Map.of(), authorities(authentication), null, null);
    }

    public SessionRecord touch(String sessionId, int interval) {
        return store.execute(() -> {
            SessionRecord prior = store.read(reference(sessionId));
            if (prior == null || expired(prior) || !"ACTIVE".equals(prior.status())) return null;
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(interval > 0 ? interval : 1_800);
            SessionRecord next = prior.touchAt(now, interval);
            return store.writeActive(next, ttl(expiry, now), false) ? next : null;
        });
    }

    private SessionIndexView view(SessionRecord record, boolean current) {
        return new SessionIndexView(record.sessionRef(), record.principal(), record.clientId(), record.status(),
                record.createdAt(), record.lastSeenAt(), record.expiresAt(), current);
    }

    private static Duration ttl(Instant expiresAt, Instant now) { return Duration.ofMillis(Math.max(1_000, expiresAt.toEpochMilli() - now.toEpochMilli())); }
    private static boolean expired(SessionRecord record) { return record.expiredAt(Instant.now()); }
    private static List<String> authorities(Authentication authentication) { return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(); }
    private static String clientId(Authentication authentication) { return authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaimAsString("client_id") != null ? jwt.getToken().getClaimAsString("client_id") : "summer-ui"; }
    public static String reference(String sessionId) { return digest(sessionId); }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
    private static void validateReference(String reference) { if (reference == null || !reference.matches("[a-f0-9]{64}")) throw new SessionReferenceInvalidException(); }
    private static String currentReference(HttpServletRequest request) { HttpSession session = request.getSession(false); return session == null ? null : reference(session.getId()); }
    private static void requireRead(Authentication authentication) { if (!has(authentication, "SCOPE_platform.capabilities.read") && !has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Session read capability denied"); }
    private static void requireWrite(Authentication authentication) { if (!has(authentication, "SCOPE_platform.capabilities.write") && !has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Session write capability denied"); }
    private static void requireAdmin(Authentication authentication) { if (!has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Administrator capability required"); }
    private static boolean has(Authentication authentication, String authority) { return authentication != null && authentication.getAuthorities().stream().anyMatch(value -> authority.equals(value.getAuthority())); }
}
