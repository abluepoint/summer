package com.abluepoint.summer.sample.realtime.infrastructure;

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
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "summer.sample.realtime.redis-enabled", havingValue = "true")
public class RealtimeMessageNodeRelayConfiguration {
    @Bean
    public RedisMessageListenerContainer realtimeMessageListenerContainer(RedisConnectionFactory factory,
                                                                    RealtimeWebSocketHub hub,
                                                                    ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            try {
                @SuppressWarnings("unchecked") Map<String, Object> envelope = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Map.class);
                Object body = envelope.get("message");
                if (body instanceof Map<?, ?> map && map.get("enterpriseId") != null) {
                    hub.broadcast(String.valueOf(map.get("enterpriseId")), envelope);
                }
            } catch (RuntimeException ignored) { }
        }, new ChannelTopic(RealtimeMessageNodeRelay.CHANNEL));
        return container;
    }
}
