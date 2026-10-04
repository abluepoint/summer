package com.abluepoint.summer.sample.operations;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDiagnosticServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void classifiesAuthenticationFailuresWithoutReturningTheConnectionDetails() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("NOAUTH Authentication required"));

        RedisDiagnosticView result = new RedisDiagnosticService(factory, clock).check();

        assertThat(result.status()).isEqualTo(RedisDiagnosticStatus.AUTHENTICATION_FAILED);
        assertThat(result.reason()).isEqualTo("REDIS_AUTHENTICATION_FAILED");
        assertThat(result.endpoint()).isEqualTo("redis");
        assertThat(result.message()).doesNotContain("redis://", "password", "NOAUTH");
    }

    @Test
    void classifiesTimeoutsAndConnectionRefusalsAsDifferentOperationalStates() {
        RedisConnectionFactory timeoutFactory = mock(RedisConnectionFactory.class);
        when(timeoutFactory.getConnection()).thenThrow(new RedisConnectionFailureException("Command timed out"));
        RedisDiagnosticView timeout = new RedisDiagnosticService(timeoutFactory, clock).check();
        assertThat(timeout.status()).isEqualTo(RedisDiagnosticStatus.TIMEOUT);
        assertThat(timeout.reason()).isEqualTo("REDIS_TIMEOUT");

        RedisConnectionFactory unavailableFactory = mock(RedisConnectionFactory.class);
        when(unavailableFactory.getConnection()).thenThrow(
                new RedisConnectionFailureException("Connection refused", new ConnectException("refused")));
        RedisDiagnosticView unavailable = new RedisDiagnosticService(unavailableFactory, clock).check();
        assertThat(unavailable.status()).isEqualTo(RedisDiagnosticStatus.UNAVAILABLE);
        assertThat(unavailable.reason()).isEqualTo("REDIS_UNAVAILABLE");
    }

    @Test
    void exposesOnlyAllowlistedNumericRuntimeMetrics() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);
        Properties info = new Properties();
        info.setProperty("uptime_in_seconds", "42");
        info.setProperty("connected_clients", "3");
        info.setProperty("used_memory", "2048");
        info.setProperty("total_commands_processed", "17");
        info.setProperty("requirepass", "should-never-be-returned");
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(connection.serverCommands()).thenReturn(serverCommands);
        when(serverCommands.info()).thenReturn(info);

        RedisDiagnosticView result = new RedisDiagnosticService(factory, clock).check();

        assertThat(result.status()).isEqualTo(RedisDiagnosticStatus.UP);
        assertThat(result.metrics().uptimeSeconds()).isEqualTo(42);
        assertThat(result.metrics().connectedClients()).isEqualTo(3);
        assertThat(result.metrics().usedMemoryBytes()).isEqualTo(2048);
        assertThat(result.metrics().totalCommandsProcessed()).isEqualTo(17);
        assertThat(result.toString()).doesNotContain("should-never-be-returned", "redis://");
    }
}
