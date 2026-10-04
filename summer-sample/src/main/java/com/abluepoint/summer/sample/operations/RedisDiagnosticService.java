package com.abluepoint.summer.sample.operations;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.Locale;
import java.util.Properties;

@Service
final class RedisDiagnosticService {

    private static final String ENDPOINT = "redis";
    private final RedisConnectionFactory connectionFactory;
    private final Clock clock;

    @Autowired
    RedisDiagnosticService(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, Clock.systemUTC());
    }

    RedisDiagnosticService(RedisConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = connectionFactory;
        this.clock = clock;
    }

    RedisDiagnosticView check() {
        Instant checkedAt = clock.instant();
        long started = System.nanoTime();
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            if (!"PONG".equalsIgnoreCase(connection.ping())) {
                return failure(RedisDiagnosticStatus.UNAVAILABLE, "REDIS_UNAVAILABLE", checkedAt, started);
            }
            return healthy(checkedAt, started, connection.serverCommands().info());
        } catch (Exception failure) {
            RedisDiagnosticStatus status = classify(failure);
            return failure(status, reason(status), checkedAt, started);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (DataAccessException ignored) {
                    // The diagnostic result is based on the command outcome. A close failure
                    // must not expose driver details or turn a successful ping into a leak.
                }
            }
        }
    }

    private RedisDiagnosticView healthy(Instant checkedAt, long started, Properties info) {
        return new RedisDiagnosticView(
                "REDIS",
                RedisDiagnosticStatus.UP,
                ENDPOINT,
                null,
                "Redis connection is healthy.",
                elapsedMillis(started),
                metrics(info),
                checkedAt
        );
    }

    private RedisDiagnosticView failure(
            RedisDiagnosticStatus status,
            String reason,
            Instant checkedAt,
            long started
    ) {
        String message = switch (status) {
            case AUTHENTICATION_FAILED -> "Redis rejected authentication.";
            case TIMEOUT -> "Redis did not respond before the configured timeout.";
            case UNAVAILABLE -> "Redis is unavailable.";
            case UP -> "Redis connection is healthy.";
        };
        return new RedisDiagnosticView(
                "REDIS", status, ENDPOINT, reason, message, elapsedMillis(started), emptyMetrics(), checkedAt
        );
    }

    private static RedisRuntimeMetrics metrics(Properties info) {
        if (info == null) return emptyMetrics();
        return new RedisRuntimeMetrics(
                number(info, "uptime_in_seconds"),
                number(info, "connected_clients"),
                number(info, "used_memory"),
                number(info, "total_commands_processed")
        );
    }

    private static Long number(Properties info, String key) {
        String value = info.getProperty(key);
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static RedisRuntimeMetrics emptyMetrics() {
        return new RedisRuntimeMetrics(null, null, null, null);
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static RedisDiagnosticStatus classify(Throwable failure) {
        if (hasCause(failure, QueryTimeoutException.class)
                || hasCause(failure, SocketTimeoutException.class)
                || hasCause(failure, TimeoutException.class)) {
            return RedisDiagnosticStatus.TIMEOUT;
        }
        String text = messages(failure);
        if (text.contains("noauth") || text.contains("wrongpass") || text.contains("authentication")) {
            return RedisDiagnosticStatus.AUTHENTICATION_FAILED;
        }
        if (text.contains("timeout") || text.contains("timed out") || text.contains("socket timeout")) {
            return RedisDiagnosticStatus.TIMEOUT;
        }
        return RedisDiagnosticStatus.UNAVAILABLE;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String reason(RedisDiagnosticStatus status) {
        return switch (status) {
            case AUTHENTICATION_FAILED -> "REDIS_AUTHENTICATION_FAILED";
            case TIMEOUT -> "REDIS_TIMEOUT";
            case UNAVAILABLE -> "REDIS_UNAVAILABLE";
            case UP -> null;
        };
    }

    private static String messages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) text.append(current.getMessage()).append(' ');
            current = current.getCause();
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }
}
