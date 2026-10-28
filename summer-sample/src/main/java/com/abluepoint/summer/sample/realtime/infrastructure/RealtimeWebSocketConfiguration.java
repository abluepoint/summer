package com.abluepoint.summer.sample.realtime.infrastructure;


import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class RealtimeWebSocketConfiguration implements WebSocketConfigurer {
    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor interceptor;
    private final String[] allowedOrigins;

    RealtimeWebSocketConfiguration(RealtimeWebSocketHandler handler, RealtimeHandshakeInterceptor interceptor,
                                   @Value("${summer.sample.realtime.allowed-origins:http://127.0.0.1:5173,http://localhost:5173}") String allowedOrigins) {
        this.handler = handler; this.interceptor = interceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/v1/messages")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
