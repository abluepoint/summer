package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
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
            if ("REVOKED".equals(record.status())) throw SummerException.withMessage(IdentityErrorCode.SESSION_REVOKED, "The server session has been revoked");
            if (!writeActive(record, ttl(expiresAt, now), true)) throw SummerException.withMessage(IdentityErrorCode.SESSION_REVOKED, "The server session has been revoked");
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
            if (prior == null || expired(prior)) throw SummerException.withMessage(IdentityErrorCode.SESSION_NOT_FOUND, "The session was not found");
            if (!prior.principal().equals(authentication.getName()) && !has(authentication, "ROLE_ADMIN")) {
                throw new AccessDeniedException("Session belongs to another principal");
            }
            SessionRecord revoked = new SessionRecord(prior.sessionRef(), prior.principal(), prior.clientId(), "REVOKED",
                    prior.createdAt(), Instant.now(), prior.expiresAt(), prior.claims(), prior.authorities());
            writeRevoked(revoked);
            return new SessionIndexOperation(reference, "REVOKED", "UNCHANGED");
        });
    }

    void revokeCurrent(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        String ref = reference(session.getId());
        execute(() -> {
            SessionRecord prior = read(ref);
            if (prior != null) {
                writeRevoked(new SessionRecord(prior.sessionRef(), prior.principal(), prior.clientId(), "REVOKED",
                        prior.createdAt(), Instant.now(), prior.expiresAt(), prior.claims(), prior.authorities()));
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
        if (record != null && "REVOKED".equals(record.status())) throw SummerException.withMessage(IdentityErrorCode.SESSION_REVOKED, "The server session has been revoked");
        if (record != null && expired(record)) throw SummerException.withMessage(IdentityErrorCode.SESSION_REVOKED, "The server session has been revoked");
    }

    SessionAuthenticationSnapshot authenticationSnapshot(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new SessionAuthenticationSnapshot(authentication.getName(), jwt.getToken().getClaims(),
                    authorities(authentication), jwt.getToken().getIssuedAt(), jwt.getToken().getExpiresAt());
        }
        return new SessionAuthenticationSnapshot(authentication.getName(), Map.of(), authorities(authentication), null, null);
    }

    @jakarta.annotation.PreDestroy
    void close() { executor.shutdownNow(); }

    private <T> T execute(Callable<T> callback) {
        long now = System.currentTimeMillis();
        long opened = openedAt.get();
        if (opened > 0 && now - opened < properties.getOpenDurationMs()) throw SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable");
        if (opened > 0) openedAt.compareAndSet(opened, 0);
        Throwable lastFailure = null;
        for (int attempt = 0; attempt <= properties.getRetries(); attempt++) {
            Future<T> future = executor.submit(callback);
            try {
                T value = future.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
                consecutiveFailures.set(0);
                return value;
            } catch (TimeoutException exception) {
                lastFailure = exception;
                future.cancel(true);
            } catch (InterruptedException exception) {
                lastFailure = exception;
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof SummerException failure
                        && (failure.errorCode() == IdentityErrorCode.SESSION_REVOKED
                        || failure.errorCode() == IdentityErrorCode.SESSION_NOT_FOUND)) throw failure;
                if (cause instanceof AccessDeniedException denied) throw denied;
                lastFailure = cause;
            }
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.getFailureThreshold()) openedAt.set(System.currentTimeMillis());
        throw SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable", lastFailure);
    }

    private SessionRecord read(String reference) {
        String json = redis.opsForValue().get(properties.getNamespace() + ":session:" + reference);
        if (json == null) return null;
        try { return mapper.readValue(json, SessionRecord.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Invalid shared session record", exception); }
    }

    // Each mutation is atomic with respect to revocation. No stale ACTIVE write can
    // overwrite the tombstone, and reads cannot recreate a missing login record.
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> ACTIVE =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local old = redis.call('GET', KEYS[1])
                if not old and ARGV[3] ~= 'create' then return 0 end
                if old then
                  local prior = cjson.decode(old)
                  local next = cjson.decode(ARGV[1])
                  if prior.status ~= 'ACTIVE' or prior.principal ~= next.principal then return 0 end
                end
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                redis.call('SADD', KEYS[2], ARGV[4])
                local priorTtl = redis.call('PTTL', KEYS[2])
                if priorTtl < tonumber(ARGV[2]) then redis.call('PEXPIRE', KEYS[2], ARGV[2]) end
                return 1
                """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> REVOKE =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local old = redis.call('GET', KEYS[1])
                if not old then return 0 end
                local record = cjson.decode(old)
                record.status = 'REVOKED'
                record.lastSeenAt = ARGV[1]
                redis.call('SET', KEYS[1], cjson.encode(record), 'KEEPTTL')
                return 1
                """, Long.class);

    SessionRecord touch(String sessionId, int interval) {
        return execute(() -> {
            SessionRecord prior = read(reference(sessionId));
            if (prior == null || expired(prior) || !"ACTIVE".equals(prior.status())) return null;
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(interval > 0 ? interval : 1_800);
            SessionRecord next = new SessionRecord(prior.sessionRef(), prior.principal(), prior.clientId(), prior.status(),
                    prior.createdAt(), now, expiry, prior.claims(), prior.authorities());
            return writeActive(next, ttl(expiry, now), false) ? next : null;
        });
    }

    private boolean writeActive(SessionRecord record, Duration ttl, boolean create) {
        try {
            Long result = redis.execute(ACTIVE, List.of(properties.getNamespace() + ":session:" + record.sessionRef(),
                    principalKey(record.principal())), mapper.writeValueAsString(record), Long.toString(ttl.toMillis()),
                    create ? "create" : "touch", record.sessionRef());
            return Long.valueOf(1).equals(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to encode shared session record", exception);
        }
    }

    private void writeRevoked(SessionRecord record) {
        redis.execute(REVOKE, List.of(properties.getNamespace() + ":session:" + record.sessionRef()), record.lastSeenAt().toString());
    }

    private SessionIndexView view(SessionRecord record, boolean current) {
        return new SessionIndexView(record.sessionRef(), record.principal(), record.clientId(), record.status(),
                record.createdAt(), record.lastSeenAt(), record.expiresAt(), current);
    }

    private static Duration ttl(Instant expiresAt, Instant now) { return Duration.ofMillis(Math.max(1_000, expiresAt.toEpochMilli() - now.toEpochMilli())); }
    private static boolean expired(SessionRecord record) { return record.expiresAt() != null && !record.expiresAt().isAfter(Instant.now()); }
    private static List<String> authorities(Authentication authentication) { return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(); }
    private static String clientId(Authentication authentication) { return authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaimAsString("client_id") != null ? jwt.getToken().getClaimAsString("client_id") : "summer-ui"; }
    private String principalKey(String principal) { return properties.getNamespace() + ":principal-sessions:" + digest(principal); }
    static String reference(String sessionId) { return digest(sessionId); }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
    private static void validateReference(String reference) { if (reference == null || !reference.matches("[a-f0-9]{64}")) throw SummerException.withMessage(IdentityErrorCode.SESSION_REFERENCE_INVALID, "The session reference must be a 64-character hexadecimal value"); }
    private static String currentReference(HttpServletRequest request) { HttpSession session = request.getSession(false); return session == null ? null : reference(session.getId()); }
    private static void requireRead(Authentication authentication) { if (!has(authentication, "SCOPE_platform.capabilities.read") && !has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Session read capability denied"); }
    private static void requireWrite(Authentication authentication) { if (!has(authentication, "SCOPE_platform.capabilities.write") && !has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Session write capability denied"); }
    private static void requireAdmin(Authentication authentication) { if (!has(authentication, "ROLE_ADMIN")) throw new AccessDeniedException("Administrator capability required"); }
    private static boolean has(Authentication authentication, String authority) { return authentication != null && authentication.getAuthorities().stream().anyMatch(value -> authority.equals(value.getAuthority())); }
}
