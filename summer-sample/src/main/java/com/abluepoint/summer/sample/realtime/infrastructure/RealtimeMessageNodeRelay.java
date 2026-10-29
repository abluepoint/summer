package com.abluepoint.summer.sample.realtime.infrastructure;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageView;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Immediate cross-node notification. Durable message state remains in the relational store. */
@Component
@ConditionalOnProperty(name = "summer.sample.realtime.redis-enabled", havingValue = "true")
public class RealtimeMessageNodeRelay {
    static final String CHANNEL = "summer:realtime:messages";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RealtimeMessageNodeRelay(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis; this.objectMapper = objectMapper;
    }

    public void publish(RealtimeMessageView message) {
        try { redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(Map.of("type", "message", "message", message))); }
        catch (RuntimeException ignored) { /* local delivery and durable persistence remain authoritative */ }
    }
}
