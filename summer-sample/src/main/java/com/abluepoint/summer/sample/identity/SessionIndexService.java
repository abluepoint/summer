package com.abluepoint.summer.sample.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Redis-backed session index with bounded retry and a small circuit breaker. */
@Service
@ConditionalOnProperty(prefix = "summer.sample.session-index", name = "enabled", havingValue = "true")
final class SessionIndexService {
    private static final String KEY_PREFIX = "summer:auth:session:";
    private static final String PRINCIPAL_PREFIX = "summer:auth:principal-sessions:";
    private static final String AVAILABLE = "AVAILABLE";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SessionIndexProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "summer-session-index");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();

    SessionIndexService(StringRedisTemplate redis, ObjectMapper mapper, SessionIndexProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    SessionIndexView ensureCurrent(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null || authentication == null || !authentication.isAuthenticated()) return null;
        String reference = reference(session.getId());
        Instant now = Instant.now();
        int interval = session.getMaxInactiveInterval();
        Instant expiresAt = now.plusSeconds(interval > 0 ? interval : 1_800);
        SessionAuthenticationSnapshot auth = authenticationSnapshot(authentication);
        return execute(() -> {
            SessionRecord old = read(reference);
            SessionRecord record = new SessionRecord(reference, authentication.getName(), clientId(authentication),
                    old != null && "REVOKED".equals(old.status()) ? "REVOKED" : "ACTIVE",
                    old == null ? now : old.createdAt(), now, expiresAt, auth.claims(), auth.authorities());
            if ("REVOKED".equals(record.status())) throw new SessionRevokedException();
            write(record, ttl(expiresAt, now));
            return view(record, true);
        });
    }

    SessionIndexPage list(String principal, Authentication authentication, HttpServletRequest request) {
        requireRead(authentication);
        String requested = principal == null || principal.isBlank() ? authentication.getName() : principal.trim();
        if (!requested.equals(authentication.getName())) requireAdmin(authentication);
        String current = currentReference(request);
        return execute(() -> {
            Set<String> references = redis.opsForSet().members(principalKey(requested));
            if (references == null || references.isEmpty()) return new SessionIndexPage(AVAILABLE, List.of());
            List<SessionIndexView> values = new ArrayList<>();
            for (String reference : references) {
                SessionRecord record = read(reference);
                if (record == null || expired(record)) {
                    redis.opsForSet().remove(principalKey(requested), reference);
                    continue;
                }
                values.add(view(record, Objects.equals(current, reference)));
            }
            values.sort(Comparator.comparing(SessionIndexView::lastSeenAt).reversed());
            return new SessionIndexPage(AVAILABLE, values);
        });
    }

    SessionIndexOperation revoke(String reference, Authentication authentication, HttpServletRequest request) {
        validateReference(reference);
        requireWrite(authentication);
        return execute(() -> {
            SessionRecord prior = read(reference);
            if (prior == null || expired(prior)) throw new SessionNotFoundException();
            if (!prior.principal().equals(authentication.getName()) && !has(authentication, "ROLE_ADMIN")) {
                throw new AccessDeniedException("Session belongs to another principal");
            }
            SessionRecord revoked = new SessionRecord(prior.sessionRef(), prior.principal(), prior.clientId(), "REVOKED",
                    prior.createdAt(), Instant.now(), prior.expiresAt(), prior.claims(), prior.authorities());
            write(revoked, ttl(prior.expiresAt(), Instant.now()));
            return new SessionIndexOperation(reference, "REVOKED", "UNCHANGED");
        });
    }

    void revokeCurrent(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        String ref = reference(session.getId());
        execute(() -> {
            SessionRecord prior = read(ref);
            if (prior != null) {
                write(new SessionRecord(prior.sessionRef(), prior.principal(), prior.clientId(), "REVOKED",
                        prior.createdAt(), Instant.now(), prior.expiresAt(), prior.claims(), prior.authorities()),
                        ttl(prior.expiresAt(), Instant.now()));
            }
            return null;
        });
    }

    SessionRecord load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        return execute(() -> read(reference(sessionId)));
    }

    void assertActive(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        SessionRecord record = load(session.getId());
        if (record != null && "REVOKED".equals(record.status())) throw new SessionRevokedException();
        if (record != null && expired(record)) throw new SessionRevokedException();
    }

    SessionAuthenticationSnapshot authenticationSnapshot(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new SessionAuthenticationSnapshot(authentication.getName(), jwt.getToken().getClaims(),
                    authorities(authentication), jwt.getToken().getIssuedAt(), jwt.getToken().getExpiresAt());
        }
        return new SessionAuthenticationSnapshot(authentication.getName(), Map.of(), authorities(authentication), null, null);
    }

    void close() { executor.shutdownNow(); }

    private <T> T execute(Callable<T> callback) {
        long now = System.currentTimeMillis();
        long opened = openedAt.get();
        if (opened > 0 && now - opened < properties.getOpenDurationMs()) throw new SessionIndexUnavailableException();
        if (opened > 0) openedAt.compareAndSet(opened, 0);
        for (int attempt = 0; attempt <= properties.getRetries(); attempt++) {
            Future<T> future = executor.submit(callback);
            try {
                T value = future.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
                consecutiveFailures.set(0);
                return value;
            } catch (TimeoutException exception) {
                future.cancel(true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof SessionRevokedException || cause instanceof SessionNotFoundException || cause instanceof AccessDeniedException) throw (RuntimeException) cause;
            }
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.getFailureThreshold()) openedAt.set(System.currentTimeMillis());
        throw new SessionIndexUnavailableException();
    }

    private SessionRecord read(String reference) {
        String json = redis.opsForValue().get(KEY_PREFIX + reference);
        if (json == null) return null;
        try { return mapper.readValue(json, SessionRecord.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Invalid shared session record"); }
    }

    private void write(SessionRecord record, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + record.sessionRef(), mapper.writeValueAsString(record), ttl);
            redis.opsForSet().add(principalKey(record.principal()), record.sessionRef());
            redis.expire(principalKey(record.principal()), ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to encode shared session record");
        }
    }

    private SessionIndexView view(SessionRecord record, boolean current) {
        return new SessionIndexView(record.sessionRef(), record.principal(), record.clientId(), record.status(),
                record.createdAt(), record.lastSeenAt(), record.expiresAt(), current);
    }

    private static Duration ttl(Instant expiresAt, Instant now) { return Duration.ofMillis(Math.max(1_000, expiresAt.toEpochMilli() - now.toEpochMilli())); }
    private static boolean expired(SessionRecord record) { return record.expiresAt() != null && !record.expiresAt().isAfter(Instant.now()); }
    private static List<String> authorities(Authentication authentication) { return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(); }
    private static String clientId(Authentication authentication) { return authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaimAsString("client_id") != null ? jwt.getToken().getClaimAsString("client_id") : "summer-ui"; }
    private static String principalKey(String principal) { return PRINCIPAL_PREFIX + digest(principal); }
    static String reference(String sessionId) { return digest(sessionId); }
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
